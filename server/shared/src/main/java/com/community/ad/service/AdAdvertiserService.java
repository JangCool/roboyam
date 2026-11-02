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
import com.community.business.service.BusinessPii;
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
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 광고주 — 운영자 등록 서랍의 광고주 고르기·[새 광고주](docs/features/ad-contract.md V115)와 광고주 관리(docs/features/ad-advertiser-contract.md, V131)의 고치기·보관·삭제·평문 열람.
 *
 * <ul>
 *   <li><b>ID</b>: 화면은 문자열 하나로 주고받는다 — 광고주 {@code adv-<광고주 ID>} · 사업자 {@code biz-<사업자 신원 ID>}({@link Ref}). 광고주 관리에서 사업자가 걸린 것은 모두
 *       {@code biz-} 한 줄이고 사업자에 붙은 광고주 줄의 {@code adv-} 는 <b>별칭</b>이다({@link #resolve} — 규칙 1). 운영자 등록 서랍의 찾기는 예전 그대로({@link #search}).</li>
 *   <li><b>연락처</b>: 담당자·연락처·이메일은 {@link DataCipher} 암호문(용도 {@code ad.advertiser.*})으로 두고, 응답에는 저장 때 만든 가린 값만 싣는다. 평문은 {@link #reveal}
 *       (권한 {@code ad.contact.reveal} — 컨트롤러가 본다) — 사유 5~200자, 열람 기록 + 감사 {@code AD_CONTACT_REVEAL}, 화면은 10분 뒤 다시 가린다(사업자 평문 열람과 같다).
 *       사업자 광고주는 광고 담당자 칸이 하나라도 있으면 그것, 없으면 사업자 신원의 대표자·연락처·이메일(규칙 8).</li>
 *   <li>사업자와 1:1 연결(선택) — 이미 연결된 사업자를 또 연결하면 400 {@code fields.businessId}.</li>
 *   <li><b>고치기·보관·삭제</b>(규칙 6·7·10): 처리 기록({@code ad_advertiser_events} — 바뀐 칸 이름만)과 감사({@code AD_ADVERTISER_*}). 보관·삭제는 직접 등록만.</li>
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

    /** 처리 기록 종류(화면 {@code AdAdvertiserAction}) */
    public static final String EVENT_CREATE = "CREATE";
    public static final String EVENT_UPDATE = "UPDATE";
    public static final String EVENT_ARCHIVE = "ARCHIVE";
    public static final String EVENT_UNARCHIVE = "UNARCHIVE";

    /** 화면이 고르는 광고주 한 줄 — {@code kind} BUSINESS(사업자 · 사업자와 연결된 광고주)·ADVERTISER(계정 없는 광고주) */
    public record Option(String id, String kind, String name, String businessId, String maskedRegNo, String contactMasked) {
    }

    /** 평문 열람 결과 — {@code hideAt} 이 지나면 화면이 다시 가린다 */
    public record Revealed(String contactName, String contactPhone, String email, Instant hideAt) {
    }

    /** 새 광고주 입력 — {@code businessId} 는 연결할 사업자 신원 ID(선택) */
    public record Create(String name, String contactName, String contactPhone, String email, String memo, String businessId) {
    }

    /** 고치기 입력 — null = 빠진 칸(그대로) · 빈 글자 = 지우기 · 값 = 바꾸기 */
    public record Update(String name, String contactName, String contactPhone, String email, String memo) {
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

    /**
     * 광고주 관리의 광고주 하나(규칙 1) — 직접 등록이면 {@code profileId} null · 광고주 줄 {@code advertiser} 필수, 사업자면 {@code profileId} + 붙은 광고주 줄(없을 수 있다).
     * {@link #key()} 가 응답 ID.
     */
    public record Target(Long profileId, AdvertiserRow advertiser) {

        public boolean business() {
            return profileId != null;
        }

        public String key() {
            return business() ? "biz-" + profileId : "adv-" + advertiser.id();
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

    /** 광고주 + 광고주와 연결되지 않은 승인 사업자 — 이름 차례, 합쳐 30개까지. 보관한 광고주는 빠진다 */
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
     * 처리 기록 {@code CREATE} + 감사 {@code AD_ADVERTISER_CREATE}(평문 없음).
     */
    public Option create(BizOperator actor, Create body) {
        return option(createRow(actor, body));
    }

    /** {@link #create} 와 같고 만든 광고주 줄을 돌려준다(광고주 관리 {@code POST /api/admin/ad-advertisers}) */
    public AdvertiserRow createRow(BizOperator actor, Create body) {
        Create b = body == null ? new Create(null, null, null, null, null, null) : body;
        BizFields f = BizFields.start();
        String name = f.text("name", b.name(), 60);
        String contactName = optional(f, "contactName", b.contactName(), 30);
        String phoneDigits = phone(f, b.contactPhone());
        String email = email(f, b.email());
        String memo = optional(f, "memo", b.memo(), 300);
        Long businessId = null;
        if (BizFields.strip(b.businessId()) != null) {
            businessId = BizFields.id(b.businessId());
            if (businessId == null) {
                f.add("businessId", "bizapp.value.invalid");
            }
        }
        f.throwIfAny();
        if (contactName != null || phoneDigits != null || email != null) {
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
            store.insertEvent(ids.nextId(), id, EVENT_CREATE, actor.operatorId(), List.of(), now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("name", name);
            if (linked != null) {
                detail.put("businessId", Long.toString(linked));
            }
            record(actor, AuditActions.AD_ADVERTISER_CREATE, Long.toString(id), detail);
            return store.find(id).orElseThrow();
        });
    }

    // ── 광고주 관리(ad-advertiser-contract) ──────────────────────────────────

    /**
     * 화면 ID → 광고주 하나(규칙 1) — {@code adv-<직접 등록>} · {@code adv-<사업자에 붙은 광고주 줄>}(별칭 → 그 사업자) · {@code biz-<신원>}(신원이 있어야 한다). 없으면 404
     * {@code AD_ADVERTISER_NOT_FOUND}. 목록에 나오는 사업자인지(규칙 2)는 광고주 관리 쪽이 따로 본다.
     */
    public Target resolve(String idText) {
        Ref ref = Ref.parse(idText);
        if (ref == null) {
            throw BizApplicationException.advertiserNotFound();
        }
        if (ref.advertiserId() != null) {
            AdvertiserRow a = store.find(ref.advertiserId()).orElseThrow(BizApplicationException::advertiserNotFound);
            return new Target(a.businessProfileId(), a);
        }
        long profile = ref.businessProfileId();
        if (store.business(profile).isEmpty()) {
            throw BizApplicationException.advertiserNotFound();
        }
        return new Target(profile, store.byProfile(profile).orElse(null));
    }

    /**
     * 고치기(규칙 7) — 빠진 칸 그대로 · 빈 글자 지우기 · 값은 바꾸기(검사는 만들기와 같다). 사업자 광고주는 {@code name} 을 보내면 400 {@code fields.name}, 담당자·연락처·이메일·메모는
     * 사업자에 붙은 광고주 줄 칸(없으면 만든다 — 이름 = 상호 스냅샷). 바뀐 칸이 없으면 기록·감사 없이 그대로. 처리 기록 {@code UPDATE}(칸 이름만) + 감사 {@code AD_ADVERTISER_UPDATE}.
     *
     * @return 고친 광고주의 화면 ID(사업자면 {@code biz-} — 광고주 줄이 새로 생겨도 바뀌지 않는다)
     */
    public String update(BizOperator actor, String idText, Update body) {
        Update b = body == null ? new Update(null, null, null, null, null) : body;
        Target first = resolve(idText);
        BizFields f = BizFields.start();
        String name = null;
        if (b.name() != null) {
            if (first.business()) {
                f.add("name", "ad.advertiser.businessName");
            } else {
                name = f.text("name", b.name(), 60);
            }
        }
        String contactName = b.contactName() == null ? null : optional(f, "contactName", b.contactName(), 30);
        String phoneDigits = b.contactPhone() == null ? null : phone(f, b.contactPhone());
        String email = b.email() == null ? null : email(f, b.email());
        String memo = b.memo() == null ? null : optional(f, "memo", b.memo(), 300);
        f.throwIfAny();
        boolean touchesContact = b.contactName() != null || b.contactPhone() != null || b.email() != null;
        if (touchesContact) {
            cipher.requireConfigured(); // 지금 값과 견주려면 풀어야 한다
        }
        String newName = name;
        return tx.execute(status -> {
            Instant now = now();
            Target t = first;
            AdvertiserRow row = t.advertiser();
            if (row != null && !store.lock(row.id())) {
                throw BizApplicationException.advertiserNotFound();
            }
            if (t.business() && row == null) {
                store.lockProfile(t.profileId());
                row = store.byProfile(t.profileId()).orElse(null);
            }
            AdAdvertiserStore.Secret secret = row == null ? new AdAdvertiserStore.Secret(null, null, null) : store.secret(row.id()).orElseThrow();
            List<String> changed = new ArrayList<>();
            String finalName = row == null ? null : row.name();
            if (newName != null && !newName.equals(row.name())) {
                if (store.nameTakenByOther(newName, row.id())) {
                    throw ValidationException.of("name", new MessageRef("ad.advertiser.nameTaken"));
                }
                finalName = newName;
                changed.add("name");
            }
            boolean nameChanged = b.contactName() != null && !Objects.equals(contactName, dec(CONTACT_NAME, secret.contactNameEnc()));
            boolean phoneChanged = b.contactPhone() != null && !Objects.equals(phoneDigits, dec(CONTACT_PHONE, secret.contactPhoneEnc()));
            boolean emailChanged = b.email() != null && !Objects.equals(email, dec(EMAIL, secret.emailEnc()));
            boolean memoChanged = b.memo() != null && !Objects.equals(memo, row == null ? null : row.memo());
            if (nameChanged) {
                changed.add("contactName");
            }
            if (phoneChanged) {
                changed.add("contactPhone");
            }
            if (emailChanged) {
                changed.add("email");
            }
            if (memoChanged) {
                changed.add("memo");
            }
            if (changed.isEmpty()) {
                return t.key();
            }
            if (row == null) {
                // 사업자 광고주의 광고 담당자를 처음 저장 — 붙은 광고주 줄을 만든다(이름 = 상호 스냅샷, 같은 이름이 있으면 신원 ID 를 붙인다)
                BusinessHit biz = store.business(t.profileId()).orElseThrow(BizApplicationException::advertiserNotFound);
                long id = ids.nextId();
                store.insert(new AdAdvertiserStore.NewAdvertiser(id, snapshotName(biz), biz.profileId(), null, null, null, null, null, null, null, actor.operatorId(),
                        now));
                row = store.find(id).orElseThrow();
                finalName = row.name();
            }
            store.update(row.id(), new AdAdvertiserStore.Change(finalName, !nameChanged, enc(CONTACT_NAME, contactName),
                    contactName == null ? null : BusinessFields.maskName(contactName), !phoneChanged, enc(CONTACT_PHONE, phoneDigits),
                    phoneDigits == null ? null : BusinessFields.maskPhone(phoneDigits), !emailChanged, enc(EMAIL, email),
                    email == null ? null : BusinessFields.maskEmail(email), !memoChanged, memo), now);
            store.insertEvent(ids.nextId(), row.id(), EVENT_UPDATE, actor.operatorId(), changed, now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("fields", changed);
            if (t.business()) {
                detail.put("businessId", Long.toString(t.profileId()));
                detail.put("advertiserId", Long.toString(row.id()));
            }
            record(actor, AuditActions.AD_ADVERTISER_UPDATE, t.business() ? t.key() : Long.toString(row.id()), detail);
            return t.key();
        });
    }

    /**
     * 보관·보관 풀기(규칙 6) — 직접 등록만(사업자 409 {@code AD_ADVERTISER_BUSINESS}). 보관은 끝나지 않은 건(진행·예약·대기)이 없을 때만(409
     * {@code AD_ADVERTISER_HAS_OPEN_BOOKINGS}), 풀기는 언제나. 이미 그 상태면 기록 없이 그대로. 처리 기록 + 감사 {@code AD_ADVERTISER_ARCHIVE}·{@code _UNARCHIVE}.
     */
    public String setArchived(BizOperator actor, String idText, boolean archived) {
        Target t = resolve(idText);
        if (t.business()) {
            throw BizApplicationException.advertiserBusiness();
        }
        long id = t.advertiser().id();
        return tx.execute(status -> {
            if (!store.lock(id)) {
                throw BizApplicationException.advertiserNotFound();
            }
            AdvertiserRow row = store.find(id).orElseThrow(BizApplicationException::advertiserNotFound);
            if (row.businessProfileId() != null) {
                throw BizApplicationException.advertiserBusiness();
            }
            if ((row.archivedAt() != null) == archived) {
                return "adv-" + id;
            }
            Instant now = now();
            if (archived && store.bookingCounts(id, now)[1] > 0) {
                throw BizApplicationException.advertiserHasOpenBookings();
            }
            store.setArchived(id, archived ? now : null, actor.operatorId(), now);
            store.insertEvent(ids.nextId(), id, archived ? EVENT_ARCHIVE : EVENT_UNARCHIVE, actor.operatorId(), List.of(), now);
            record(actor, archived ? AuditActions.AD_ADVERTISER_ARCHIVE : AuditActions.AD_ADVERTISER_UNARCHIVE, Long.toString(id), Map.of("name", row.name()));
            return "adv-" + id;
        });
    }

    /**
     * 삭제(규칙 6) — 직접 등록이고 신청 건이 하나도 없을 때만(409 {@code AD_ADVERTISER_HAS_BOOKINGS}, 사업자 409 {@code AD_ADVERTISER_BUSINESS}). 광고주 줄·열람 기록·처리 기록을
     * 지우고 감사 {@code AD_ADVERTISER_DELETE} 는 남긴다.
     */
    public void delete(BizOperator actor, String idText) {
        Target t = resolve(idText);
        if (t.business()) {
            throw BizApplicationException.advertiserBusiness();
        }
        long id = t.advertiser().id();
        tx.executeWithoutResult(status -> {
            if (!store.lock(id)) {
                throw BizApplicationException.advertiserNotFound();
            }
            AdvertiserRow row = store.find(id).orElseThrow(BizApplicationException::advertiserNotFound);
            if (row.businessProfileId() != null) {
                throw BizApplicationException.advertiserBusiness();
            }
            if (store.bookingCounts(id, now())[0] > 0) {
                throw BizApplicationException.advertiserHasBookings();
            }
            store.delete(id);
            record(actor, AuditActions.AD_ADVERTISER_DELETE, Long.toString(id), Map.of("name", row.name()));
        });
    }

    /**
     * 연락처 평문 열람 — 사유 5~200자(400 {@code fields.reason}), 열람 기록 + 감사. 없는 광고주 404 {@code AD_ADVERTISER_NOT_FOUND}.
     * 사업자 광고주({@code biz-} · 별칭 {@code adv-})는 광고 담당자 칸이 하나라도 있으면 그것, 없으면 사업자 신원 대표자·연락처·이메일 — 열람 기록은 신원 키로(규칙 8).
     */
    public Revealed reveal(BizOperator actor, String idText, String reasonText) {
        String reason = BizFields.strip(reasonText);
        if (reason == null || reason.length() < 5 || reason.length() > 200) {
            throw ValidationException.of("reason", new MessageRef("ad.reason.invalid", 5, 200));
        }
        Target t = resolve(idText);
        return tx.execute(status -> {
            Instant now = now();
            if (!t.business()) {
                long id = t.advertiser().id();
                AdAdvertiserStore.Secret secret = store.secret(id).orElseThrow(BizApplicationException::advertiserNotFound);
                store.insertView(ids.nextId(), id, actor.operatorId(), reason, actor.ip(), now);
                record(actor, AuditActions.AD_CONTACT_REVEAL, Long.toString(id), Map.of("reason", reason));
                return new Revealed(dec(CONTACT_NAME, secret.contactNameEnc()), BusinessFields.formatPhone(dec(CONTACT_PHONE, secret.contactPhoneEnc())),
                        dec(EMAIL, secret.emailEnc()), now.plus(REVEAL_TTL));
            }
            long profile = t.profileId();
            AdvertiserRow row = store.byProfile(profile).orElse(null);
            Revealed out;
            String source;
            if (row != null && row.hasContact()) {
                AdAdvertiserStore.Secret secret = store.secret(row.id()).orElseThrow(BizApplicationException::advertiserNotFound);
                source = "ADVERTISER";
                out = new Revealed(dec(CONTACT_NAME, secret.contactNameEnc()), BusinessFields.formatPhone(dec(CONTACT_PHONE, secret.contactPhoneEnc())),
                        dec(EMAIL, secret.emailEnc()), now.plus(REVEAL_TTL));
            } else {
                AdAdvertiserStore.BusinessSecret secret = store.businessSecret(profile).orElseThrow(BizApplicationException::advertiserNotFound);
                source = "BUSINESS";
                out = new Revealed(dec(BusinessPii.OWNER, secret.ownerNameEnc()), BusinessFields.formatPhone(dec(BusinessPii.PHONE, secret.phoneEnc())),
                        dec(BusinessPii.EMAIL, secret.emailEnc()), now.plus(REVEAL_TTL));
            }
            store.insertBusinessView(ids.nextId(), profile, source, actor.operatorId(), reason, actor.ip(), now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("reason", reason);
            detail.put("source", source);
            record(actor, AuditActions.AD_CONTACT_REVEAL, t.key(), detail);
            return out;
        });
    }

    /** 광고주 줄 → 화면 한 줄(연결된 사업자면 BUSINESS + 사업자 ID·등록번호) — 운영자 등록 서랍의 찾기 모양(ID {@code adv-} 그대로) */
    public static Option option(AdvertiserRow a) {
        boolean business = a.businessProfileId() != null;
        String contact = a.contactPhoneMasked() != null ? a.contactPhoneMasked() : a.emailMasked();
        return new Option("adv-" + a.id(), business ? "BUSINESS" : "ADVERTISER", a.name(), business ? Long.toString(a.businessProfileId()) : null,
                business ? BusinessFields.maskBizNo(a.bizNoLast4()) : null, contact);
    }

    /** 사업자 광고주의 광고주 줄 이름 — 상호 그대로, 같은 이름이 있으면 「상호 (신원 ID)」(60자 안) */
    private String snapshotName(BusinessHit biz) {
        String name = biz.companyName().strip();
        if (!store.nameTaken(name)) {
            return name;
        }
        String suffix = " (" + biz.profileId() + ")";
        return name.substring(0, Math.min(name.length(), 60 - suffix.length())) + suffix;
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

    /** 연락처 — 20자 안, 숫자 9~11자리(숫자만 남겨 저장) · 비었으면 null */
    private static String phone(BizFields f, String value) {
        String phone = optional(f, "contactPhone", value, 20);
        String digits = phone == null ? null : phone.replaceAll("[^0-9]", "");
        if (phone != null && (digits.length() < 9 || digits.length() > 11)) {
            f.add("contactPhone", "ad.advertiser.phone");
        }
        return digits;
    }

    /** 이메일 — 120자 안, 형식 · 비었으면 null */
    private static String email(BizFields f, String value) {
        String email = optional(f, "email", value, 120);
        if (email != null && !EMAIL_SHAPE.matcher(email).matches()) {
            f.add("email", "ad.advertiser.email");
        }
        return email;
    }

    private void record(BizOperator actor, String action, String targetId, Map<String, Object> detail) {
        audit.record(new AuditEvent(AuditActorType.OPERATOR, actor.operatorId(), action, AuditResult.SUCCESS, AUDIT_TARGET, targetId, actor.ip(), detail));
    }

    /** 감사 대상 종류 */
    public static final String AUDIT_TARGET = "AD_ADVERTISER";

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
