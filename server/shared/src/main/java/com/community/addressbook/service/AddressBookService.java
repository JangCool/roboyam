package com.community.addressbook.service;

import com.community.addressbook.dto.AddressInput;
import com.community.addressbook.dto.SavedAddress;
import com.community.addressbook.exception.AddressBookFullException;
import com.community.addressbook.exception.AddressNotFoundException;
import com.community.addressbook.repository.master.MemberAddressStore;
import com.community.addressbook.repository.master.MemberAddressStore.Row;
import com.community.groupbuy.dto.ShippingAddress;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.crypto.DataPurpose;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 주소록(배송지 관리 — docs/features/address-book-contract.md). 회원 본인만 쓰고(남의 배송지는 404 {@code ADDRESS_NOT_FOUND}), 운영자 쪽에는 없다.
 *
 * <ul>
 *   <li>저장은 {@link DataCipher} 암호문(용도 {@code member.address.*}), 우편번호·별칭만 평문. 키가 없으면 모든 길이 503 {@code DATA_KEY_MISSING}(주문과 같게).</li>
 *   <li>칸 규칙은 주문 배송지와 같은 {@link AddressFieldRules} + 별칭 0~20자(비우면 null). 틀린 칸을 모아 400 {@code VALIDATION}.</li>
 *   <li>한 회원 최대 {@value #MAX}개(넘으면 409 {@code ADDRESS_BOOK_FULL}). 첫 배송지는 자동 기본, 기본은 늘 하나, 기본을 지우면 남은 것 중 가장 최근({@code updated_at}) 것이 기본,
 *       기본을 끄는 길은 다른 배송지를 기본으로 정하는 것뿐. 모든 쓰기는 회원 단위 잠금({@link MemberAddressStore#lockMember}) 아래에서 한다 — 동시 요청에도 한도·기본 하나를 지킨다.</li>
 *   <li>목록 차례: 기본 → 최근 고친 순. 로그·예외 문구에 값을 싣지 않는다.</li>
 * </ul>
 */
@Service
public class AddressBookService {

    private static final Logger log = LoggerFactory.getLogger(AddressBookService.class);

    /** 한 회원이 둘 수 있는 배송지 수 */
    public static final int MAX = 10;
    public static final int LABEL_MAX = 20;

    /** 주소록 칸 용도(AAD — 주문 배송지 {@code groupbuy.address.*} 와 다르다. 한 번 정하면 바꾸지 않는다) */
    public static final DataPurpose RECIPIENT = DataPurpose.of("member.address.recipient");
    public static final DataPurpose PHONE = DataPurpose.of("member.address.phone");
    public static final DataPurpose ADDRESS1 = DataPurpose.of("member.address.line1");
    public static final DataPurpose ADDRESS2 = DataPurpose.of("member.address.line2");
    public static final DataPurpose MEMO = DataPurpose.of("member.address.memo");

    private final MemberAddressStore store;
    private final DataCipher cipher;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public AddressBookService(MemberAddressStore store, DataCipher cipher, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.cipher = cipher;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 내 배송지 목록 — 기본 → 최근 고친 순 */
    @Transactional(readOnly = true)
    public List<SavedAddress> list(long userId) {
        cipher.requireConfigured();
        return store.list(userId).stream().map(this::open).toList();
    }

    /**
     * 배송지를 더한다. 첫 배송지는 {@code makeDefault} 와 상관없이 기본.
     *
     * @throws ValidationException      칸
     * @throws AddressBookFullException 이미 {@value #MAX}개
     */
    @Transactional
    public SavedAddress create(long userId, AddressInput input) {
        validate(input);
        cipher.requireConfigured();
        store.lockMember(userId);
        int count = store.count(userId);
        if (count >= MAX) {
            throw new AddressBookFullException(MAX);
        }
        boolean isDefault = count == 0 || input.makeDefault();
        if (isDefault) {
            store.clearDefault(userId);
        }
        long id = ids.nextId();
        store.insert(id, userId, seal(input), isDefault, now());
        return open(store.find(userId, id).orElseThrow());
    }

    /**
     * 배송지를 고친다. {@code makeDefault: true} 면 이것을 기본으로 — {@code false} 로 기본을 끌 수는 없다(기본은 그대로).
     *
     * @throws ValidationException      칸
     * @throws AddressNotFoundException 없거나 남의 배송지
     */
    @Transactional
    public SavedAddress update(long userId, long id, AddressInput input) {
        validate(input);
        cipher.requireConfigured();
        store.lockMember(userId);
        Row row = store.find(userId, id).orElseThrow(AddressNotFoundException::new);
        store.update(userId, id, seal(input), now());
        if (input.makeDefault() && !row.isDefault()) {
            store.makeDefault(userId, id);
        }
        return open(store.find(userId, id).orElseThrow());
    }

    /** 지운다(바로 삭제). 기본을 지웠으면 남은 것 중 가장 최근 것이 기본 */
    @Transactional
    public void delete(long userId, long id) {
        cipher.requireConfigured();
        store.lockMember(userId);
        Row row = store.find(userId, id).orElseThrow(AddressNotFoundException::new);
        store.delete(userId, id);
        if (row.isDefault()) {
            store.promoteNewestIfNoDefault(userId);
        }
    }

    /** 기본 배송지로 정한다(앞 기본은 꺼진다) */
    @Transactional
    public void makeDefault(long userId, long id) {
        cipher.requireConfigured();
        store.lockMember(userId);
        store.find(userId, id).orElseThrow(AddressNotFoundException::new);
        store.makeDefault(userId, id);
    }

    /**
     * 공구 주문이 고른 저장 배송지 — 복호화한 값(주문 배송지로 복사한다). 없거나 남의 것이면 비어 있다.
     *
     * @throws com.community.zconfiguration.exception.CommonException 키가 없을 때 503 {@code DATA_KEY_MISSING}
     */
    @Transactional(readOnly = true)
    public Optional<SavedAddress> forOrder(long userId, long id) {
        cipher.requireConfigured();
        return store.find(userId, id).map(this::open);
    }

    /**
     * 주문서에서 새로 쓴 배송지를 주소록에 저장한다 — <b>주문 생성 트랜잭션 안에서만</b>(주문이 실패하면 저장도 없다. 멱등 키로 다시 보낸 요청은 업무가 다시 돌지 않아 두 번 저장되지 않는다).
     * 이미 {@value #MAX}개면 저장만 건너뛴다(주문은 계속). 주소록이 비어 있었으면 이것이 기본. 별칭은 없다. 칸은 주문이 이미 같은 규칙({@link AddressFieldRules})으로 검사했다.
     *
     * @return 저장했으면 true
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean saveFromOrder(long userId, ShippingAddress address) {
        store.lockMember(userId);
        int count = store.count(userId);
        if (count >= MAX) {
            log.info("주소록이 가득 차 주문 배송지를 저장하지 않았다 userId={}", userId);
            return false;
        }
        store.insert(ids.nextId(), userId, seal(new AddressInput(null, address.recipient(), address.phone(), address.postcode(), address.address1(), address.address2(),
                address.memo(), false)), count == 0, now());
        return true;
    }

    /** 칸 규칙(주문 배송지와 같은 것) + 별칭 0~20자 */
    static void validate(AddressInput input) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        if (input.label() != null && input.label().strip().length() > LABEL_MAX) {
            fields.put("label", new MessageRef("address.field.label", LABEL_MAX));
        }
        fields.putAll(AddressFieldRules.violations("", input.recipient(), input.phone(), input.postcode(), input.address1(), input.address2(), input.memo()));
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
    }

    private MemberAddressStore.Values seal(AddressInput in) {
        String label = in.label() == null || in.label().isBlank() ? null : in.label().strip();
        return new MemberAddressStore.Values(label, cipher.encrypt(RECIPIENT, in.recipient().strip()), cipher.encrypt(PHONE, in.phone().strip()), in.postcode().strip(),
                cipher.encrypt(ADDRESS1, in.address1().strip()), enc(ADDRESS2, in.address2()), enc(MEMO, in.memo()));
    }

    private SavedAddress open(Row row) {
        return new SavedAddress(row.id(), row.label(), cipher.decrypt(RECIPIENT, row.recipientEnc()), cipher.decrypt(PHONE, row.phoneEnc()), row.postcode(),
                cipher.decrypt(ADDRESS1, row.address1Enc()), dec(ADDRESS2, row.address2Enc()), dec(MEMO, row.memoEnc()), row.isDefault(), row.updatedAt());
    }

    private String enc(DataPurpose purpose, String value) {
        return value == null || value.isBlank() ? null : cipher.encrypt(purpose, value.strip());
    }

    private String dec(DataPurpose purpose, String value) {
        return value == null ? "" : cipher.decrypt(purpose, value);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
