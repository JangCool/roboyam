package com.community.ad.service;

import com.community.ad.repository.master.AdAdvertiserStore;
import com.community.ad.repository.master.AdAdvertiserStore.AdvertiserRow;
import com.community.ad.repository.master.AdAdvertiserStore.BusinessHit;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.bizapplication.dto.BizOperator;
import com.community.bizapplication.exception.BizApplicationException;
import com.community.bizapplication.service.BizFields;
import com.community.business.service.BusinessFields;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.crypto.DataPurpose;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 광고주(admin-web 광고 일정표 수동 등록 서랍의 광고주 고르기·[새 광고주]) — 사업자 신원과 계정 없는 광고주({@code ad_advertisers})를 한 목록으로 찾고, 계정 없는 광고주를 만든다.
 *
 * <ul>
 *   <li><b>ID</b>: 화면은 문자열 하나로 주고받는다 — 광고주 {@code adv-<광고주 ID>} · 광고주와 연결되지 않은 사업자 {@code biz-<사업자 신원 ID>}({@link Ref}).</li>
 *   <li><b>연락처</b>: 담당자·연락처·이메일은 {@link DataCipher} 암호문(용도 {@code ad.advertiser.*})으로 두고, 응답에는 저장 때 만든 가린 값만 싣는다. 평문은 {@link #reveal}
 *       (권한 {@code ad.contact.reveal} — 컨트롤러가 본다) — 사유 5~200자, 열람 기록 + 감사 {@code AD_CONTACT_REVEAL}, 화면은 10분 뒤 다시 가린다(사업자 평문 열람과 같다).</li>
 *   <li>사업자와 1:1 연결(선택) — 연결된 광고주는 사업자 쪽 이름·등록번호 끝 4자리도 같이 보인다. 이미 연결된 사업자를 또 연결하면 400 {@code fields.businessId}.</li>
 * </ul>
 */
@Service
public class AdAdvertiserService {

    public static final DataPurpose CONTACT_NAME = DataPurpose.of("ad.advertiser.contact-name");
    public static final DataPurpose CONTACT_PHONE = DataPurpose.of("ad.advertiser.phone");
    public static final DataPurpose EMAIL = DataPurpose.of("ad.advertiser.email");
    static final Duration REVEAL_TTL = Duration.ofMinutes(10);
    static final int SEARCH_LIMIT = 30;
    private static final Pattern EMAIL_SHAPE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /** 화면이 고르는 광고주 한 줄 — {@code kind} BUSINESS(사업자 · 사업자와 연결된 광고주)·ADVERTISER(계정 없는 광고주) */
    public record Option(String id, String kind, String name, String businessId, String maskedRegNo, String contactMasked) {
    }

    /** 평문 열람 결과 — {@code hideAt} 이 지나면 화면이 다시 가린다 */
    public record Revealed(String contactName, String contactPhone, String email, Instant hideAt) {
    }

    /** 새 광고주 입력 — {@code businessId} 는 연결할 사업자 신원 ID(선택) */
    public record Create(String name, String contactName, String contactPhone, String email, String memo, String businessId) {
    }

    /** 화면 ID 풀이 — 광고주 또는 사업자 */
    public record Ref(Long advertiserId, Long businessProfileId) {

        public static Ref parse(String text) {
            String t = text == null ? "" : text.strip();
            try {
                if (t.startsWith("adv-")) {
                    return new Ref(Long.parseLong(t.substring(4)), null);
                }
                if (t.startsWith("biz-")) {
                    return new Ref(null, Long.parseLong(t.substring(4)));
                }
            } catch (NumberFormatException ignored) {
                // 아래에서 null
            }
            return null;
        }
    }

    private final AdAdvertiserStore store;
    private final DataCipher cipher;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;
    private final TransactionTemplate tx;

    public AdAdvertiserService(AdAdvertiserStore store, DataCipher cipher, AuditLogService audit, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks,
                               PlatformTransactionManager transactions) {
        this.store = store;
        this.cipher = cipher;
        this.audit = audit;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
        this.tx = new TransactionTemplate(transactions);
    }

    /** 광고주 + 광고주와 연결되지 않은 승인 사업자 — 이름 차례, 합쳐 30개까지 */
    public List<Option> search(String q) {
        List<Option> out = new ArrayList<>();
        for (AdvertiserRow a : store.search(q, SEARCH_LIMIT)) {
            out.add(option(a));
        }
        for (BusinessHit b : store.searchBusinesses(q, SEARCH_LIMIT)) {
            out.add(new Option("biz-" + b.profileId(), "BUSINESS", b.companyName(), Long.toString(b.profileId()), BusinessFields.maskBizNo(b.bizNoLast4()), null));
        }
        out.sort((x, y) -> x.name().compareToIgnoreCase(y.name()));
        return out.size() <= SEARCH_LIMIT ? out : out.subList(0, SEARCH_LIMIT);
    }

    /**
     * 계정 없는 광고주 만들기 — 이름 1~60자(같은 이름 400 {@code fields.name}), 담당자 0~30자, 연락처 숫자 9~11자리, 이메일 형식 120자, 메모 300자, 연결 사업자(선택).
     * 감사 {@code AD_ADVERTISER_CREATE}(평문 없음).
     */
    public Option create(BizOperator actor, Create body) {
        Create b = body == null ? new Create(null, null, null, null, null, null) : body;
        BizFields f = BizFields.start();
        String name = f.text("name", b.name(), 60);
        String contactName = optional(f, "contactName", b.contactName(), 30);
        String phone = optional(f, "contactPhone", b.contactPhone(), 20);
        String phoneDigits = phone == null ? null : phone.replaceAll("[^0-9]", "");
        if (phone != null && (phoneDigits.length() < 9 || phoneDigits.length() > 11)) {
            f.add("contactPhone", "ad.advertiser.phone");
        }
        String email = optional(f, "email", b.email(), 120);
        if (email != null && !EMAIL_SHAPE.matcher(email).matches()) {
            f.add("email", "ad.advertiser.email");
        }
        String memo = optional(f, "memo", b.memo(), 300);
        Long businessId = null;
        if (BizFields.strip(b.businessId()) != null) {
            businessId = BizFields.id(b.businessId());
            if (businessId == null) {
                f.add("businessId", "bizapp.value.invalid");
            }
        }
        f.throwIfAny();
        if (contactName != null || phone != null || email != null) {
            cipher.requireConfigured();
        }
        Long linked = businessId;
        return tx.execute(status -> {
            if (store.nameTaken(name)) {
                throw ValidationException.of("name", new MessageRef("ad.advertiser.nameTaken"));
            }
            if (linked != null && (store.business(linked).isEmpty() || store.byProfile(linked).isPresent())) {
                throw ValidationException.of("businessId", new MessageRef("ad.advertiser.businessLinked"));
            }
            long id = ids.nextId();
            Instant now = now();
            store.insert(new AdAdvertiserStore.NewAdvertiser(id, name, linked, enc(CONTACT_NAME, contactName), enc(CONTACT_PHONE, phoneDigits), enc(EMAIL, email),
                    contactName == null ? null : BusinessFields.maskName(contactName), phoneDigits == null ? null : BusinessFields.maskPhone(phoneDigits),
                    email == null ? null : BusinessFields.maskEmail(email), memo, actor.operatorId(), now));
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("name", name);
            if (linked != null) {
                detail.put("businessId", Long.toString(linked));
            }
            record(actor, AuditActions.AD_ADVERTISER_CREATE, id, detail);
            return option(store.find(id).orElseThrow());
        });
    }

    /** 연락처 평문 열람 — 사유 5~200자(400 {@code fields.reason}), 열람 기록 + 감사. 없는 광고주 404 {@code AD_ADVERTISER_NOT_FOUND} */
    public Revealed reveal(BizOperator actor, String idText, String reasonText) {
        Ref ref = Ref.parse(idText);
        String reason = BizFields.strip(reasonText);
        if (reason == null || reason.length() < 5 || reason.length() > 200) {
            throw ValidationException.of("reason", new MessageRef("ad.reason.invalid", 5, 200));
        }
        if (ref == null || ref.advertiserId() == null) {
            throw BizApplicationException.advertiserNotFound();
        }
        long id = ref.advertiserId();
        return tx.execute(status -> {
            AdAdvertiserStore.Secret secret = store.secret(id).orElseThrow(BizApplicationException::advertiserNotFound);
            Instant now = now();
            store.insertView(ids.nextId(), id, actor.operatorId(), reason, actor.ip(), now);
            record(actor, AuditActions.AD_CONTACT_REVEAL, id, Map.of("reason", reason));
            return new Revealed(dec(CONTACT_NAME, secret.contactNameEnc()), BusinessFields.formatPhone(dec(CONTACT_PHONE, secret.contactPhoneEnc())),
                    dec(EMAIL, secret.emailEnc()), now.plus(REVEAL_TTL));
        });
    }

    /** 광고주 줄 → 화면 한 줄(연결된 사업자면 BUSINESS + 사업자 ID·등록번호) */
    public static Option option(AdvertiserRow a) {
        boolean business = a.businessProfileId() != null;
        String contact = a.contactPhoneMasked() != null ? a.contactPhoneMasked() : a.emailMasked();
        return new Option("adv-" + a.id(), business ? "BUSINESS" : "ADVERTISER", a.name(), business ? Long.toString(a.businessProfileId()) : null,
                business ? BusinessFields.maskBizNo(a.bizNoLast4()) : null, contact);
    }

    private String enc(DataPurpose purpose, String plain) {
        return plain == null ? null : cipher.encrypt(purpose, plain);
    }

    private String dec(DataPurpose purpose, String cipherText) {
        return cipherText == null ? null : cipher.decrypt(purpose, cipherText);
    }

    private static String optional(BizFields f, String field, String value, int max) {
        String v = BizFields.strip(value);
        if (v != null && v.length() > max) {
            f.add(field, "bizapp.field.tooLong", max);
            return null;
        }
        return v;
    }

    private void record(BizOperator actor, String action, long id, Map<String, Object> detail) {
        audit.record(new AuditEvent(AuditActorType.OPERATOR, actor.operatorId(), action, AuditResult.SUCCESS, AUDIT_TARGET, Long.toString(id), actor.ip(), detail));
    }

    /** 감사 대상 종류 */
    public static final String AUDIT_TARGET = "AD_ADVERTISER";

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
