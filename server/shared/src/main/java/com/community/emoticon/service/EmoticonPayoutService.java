package com.community.emoticon.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.business.service.BusinessFields;
import com.community.emoticon.dto.request.EmoticonPayoutAccountRequest;
import com.community.emoticon.dto.response.EmoticonAdminView;
import com.community.emoticon.dto.response.EmoticonStudioView;
import com.community.emoticon.exception.EmoticonConflictException;
import com.community.emoticon.exception.EmoticonPayoutNotFoundException;
import com.community.emoticon.repository.master.EmoticonPayoutStore;
import com.community.emoticon.repository.master.EmoticonPayoutStore.PayoutRow;
import com.community.groupbuy.service.AddressRevealService;
import com.community.identity.exception.IdentityRequiredException;
import com.community.identity.repository.master.IdentityStore;
import com.community.identity.service.IdentityVerificationService;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.crypto.DataPurpose;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로보티콘 작가 정산 정보(계약 가정 5 — S2). 유료 판매 희망 신청·유료 승인의 조건이고, 정산(S4)이 지급할 계좌다.
 *
 * <ul>
 *   <li><b>보기</b>({@code GET …/payout-account}): 가린 값만 — 사업자 번호 {@code 123-45-*****} · 계좌 {@code ****1234} · 예금주 {@code 홍*동} · 인증 이름(가림, 미인증이면 null).</li>
 *   <li><b>저장</b>({@code PUT}) 차례: 작가 자격(정상 회원 403 · 이메일 인증 403) → 본인인증 {@code 403 IDENTITY_REQUIRED}(계정에 한 번 — 이미 했으면 다시 묻지 않는다) →
 *       만 19세 미만 {@code 403 IDENTITY_UNDERAGE}(+{@code adultFrom} — 후속 결정 3, 무료 팩 등록·판매는 된다) → 인증 이름이 남아 있지 않음 {@code 409 EMOTICON_IDENTITY_NAME_MISSING}(V143 전에 인증한 회원) → 칸 {@code 400}(모아서 — {@code type} · {@code businessNumber}(사업자만, 10자리 +
 *       국세청 검증 숫자) · {@code bankCode}(11가지) · {@code accountNumber}(숫자 10~14자리, 하이픈·띄어쓰기는 뗀다) · {@code holderName}(인증 이름과 같아야 — 띄어쓰기 무시)) →
 *       데이터 키 없음 {@code 503 DATA_KEY_MISSING} → 넣거나 바꾼다. 원문은 {@link DataCipher} 암호문으로만.</li>
 *   <li><b>원천징수 판단</b>: {@code INDIVIDUAL} = 개인 작가(사업소득 3.3% — S4 계산), {@code BUSINESS} = 사업자 작가(세금계산서 — 원천징수 0). S2 는 구분만 저장한다.</li>
 *   <li><b>운영자 원문 보기</b>({@link #reveal}): 권한 {@code emoticon.payout.pii}(컨트롤러) + 사유 5~200자 → 없으면 404 → 감사 {@code EMOTICON_PAYOUT_REVEAL}(값은 싣지 않는다)를
 *       같은 트랜잭션에 남긴 뒤에만 푼다. 화면은 {@value #REVEAL_MINUTES}분 뒤 다시 가린다.</li>
 * </ul>
 */
@Service
public class EmoticonPayoutService {

    private static final Logger log = LoggerFactory.getLogger(EmoticonPayoutService.class);

    public static final String TARGET = "EMOTICON_PAYOUT_ACCOUNT";
    /** 암호문 용도 — 한 번 정하면 바꾸지 않는다(키 교체 배치 DataKeyColumns 와 같은 상수) */
    public static final DataPurpose ACCOUNT = DataPurpose.of("emoticon.payout.account");
    public static final DataPurpose HOLDER = DataPurpose.of("emoticon.payout.holder");
    public static final DataPurpose BUSINESS_NUMBER = DataPurpose.of("emoticon.payout.business-number");
    /** 은행 코드 — user-web {@code UtilEmoticon.banks()} · V143 CHECK 와 같다 */
    public static final List<String> BANKS = List.of("KB", "SHINHAN", "WOORI", "HANA", "NH", "IBK", "KAKAO", "TOSS", "KBANK", "SC", "POST");
    public static final int ACCOUNT_MIN = 10;
    public static final int ACCOUNT_MAX = 14;
    public static final int REVEAL_MINUTES = 10;

    private final EmoticonPayoutStore store;
    private final IdentityStore identities;
    private final EmoticonStudioService studio;
    private final DataCipher cipher;
    private final AuditLogService audit;
    private final Clock clock;

    public EmoticonPayoutService(EmoticonPayoutStore store, IdentityStore identities, EmoticonStudioService studio, DataCipher cipher, AuditLogService audit,
                                 ObjectProvider<Clock> clocks) {
        this.store = store;
        this.identities = identities;
        this.studio = studio;
        this.cipher = cipher;
        this.audit = audit;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Transactional(readOnly = true)
    public EmoticonStudioView.PayoutAccount view(long userId) {
        String identityMasked = identityName(userId).map(AddressRevealService::maskName).orElse(null);
        return store.find(userId).map(r -> view(r, identityMasked))
                .orElseGet(() -> new EmoticonStudioView.PayoutAccount(false, null, null, null, null, null, identityMasked, null));
    }

    @Transactional
    public EmoticonStudioView.PayoutAccount save(long userId, EmoticonPayoutAccountRequest body) {
        studio.requireCreator(userId);
        if (identities.findVerification(userId).isEmpty()) {
            throw new IdentityRequiredException();
        }
        studio.requireAdultCreator(userId); // 만 19세 미만은 정산 정보를 등록할 수 없다(후속 결정 3 — 403 IDENTITY_UNDERAGE)
        String identityName = identityName(userId).orElseThrow(() -> EmoticonConflictException.identityNameMissing(cipher.isConfigured()));
        EmoticonPayoutAccountRequest b = body == null ? new EmoticonPayoutAccountRequest(null, null, null, null, null) : body;
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        String type = b.type();
        boolean business = "BUSINESS".equals(type);
        if (!business && !"INDIVIDUAL".equals(type)) {
            fields.put("type", new MessageRef("emoticon.payout.type"));
        }
        String biz = business ? BusinessFields.digits(b.businessNumber()) : null;
        if (business && !BusinessFields.validBizNo(biz)) {
            fields.put("businessNumber", new MessageRef("emoticon.payout.businessNumber"));
        }
        if (b.bankCode() == null || !BANKS.contains(b.bankCode())) {
            fields.put("bankCode", new MessageRef("emoticon.payout.bankCode"));
        }
        String rawAccount = b.accountNumber() == null ? "" : b.accountNumber().replaceAll("[\\s-]", "");
        if (!rawAccount.matches("[0-9]{" + ACCOUNT_MIN + "," + ACCOUNT_MAX + "}")) {
            fields.put("accountNumber", new MessageRef("emoticon.payout.accountNumber", ACCOUNT_MIN, ACCOUNT_MAX));
        }
        String holder = b.holderName() == null ? "" : b.holderName().strip();
        if (holder.isEmpty()) {
            fields.put("holderName", new MessageRef("emoticon.payout.holderName.required"));
        } else if (!compact(holder).equals(compact(identityName))) {
            fields.put("holderName", new MessageRef("emoticon.payout.holderName.mismatch"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        cipher.requireConfigured();
        Instant now = clock.instant();
        Instant created = store.find(userId).map(PayoutRow::createdAt).orElse(now);
        store.upsert(new PayoutRow(userId, type, business ? cipher.encrypt(BUSINESS_NUMBER, biz) : null, business ? maskBizNo(biz) : null, b.bankCode(),
                cipher.encrypt(ACCOUNT, rawAccount), rawAccount.substring(rawAccount.length() - 4), cipher.encrypt(HOLDER, identityName), AddressRevealService.maskName(identityName),
                created, now));
        log.info("로보티콘 정산 정보 저장 userId={} type={} bank={}", userId, type, b.bankCode());
        return view(userId);
    }

    /** 정산 정보를 등록했나 */
    public boolean registered(long userId) {
        return store.registered(userId);
    }

    public Set<Long> registered(Collection<Long> userIds) {
        return store.registered(userIds);
    }

    /** 운영자 원문 보기 — 사유 → 404 → 감사(같은 트랜잭션) → 값 */
    @Transactional
    public EmoticonAdminView.PayoutAccountReveal reveal(long operatorId, String ip, String memberNoText, String reasonText) {
        String reason = EmoticonPackAdminService.reason(reasonText);
        long memberNo = EmoticonShopService.parse(memberNoText);
        return reveal(operatorId, ip, memberNo, reason, null);
    }

    /**
     * 정산 줄 주소의 원문 보기(S4 — {@code EmoticonSettlementService.revealAccount}). 사유는 부르는 쪽이 이미 검사했다. 감사에 그 정산 줄 ID({@code settlement})를 더한다.
     */
    @Transactional
    public EmoticonAdminView.PayoutAccountReveal reveal(long operatorId, String ip, long memberNo, String reason, String settlementId) {
        PayoutRow row = (memberNo < 0 ? Optional.<PayoutRow>empty() : store.find(memberNo)).orElseThrow(EmoticonPayoutNotFoundException::new);
        AuditEvent event = AuditEvent.byOperator(operatorId, ip, AuditActions.EMOTICON_PAYOUT_REVEAL, AuditResult.SUCCESS).target(TARGET, Long.toString(memberNo))
                .with("reason", reason);
        if (settlementId != null) {
            event = event.with("settlement", settlementId);
        }
        audit.record(event);
        Instant now = clock.instant();
        return new EmoticonAdminView.PayoutAccountReveal(row.payeeType(), row.bankCode(), cipher.decrypt(ACCOUNT, row.accountNumberCipher()),
                cipher.decrypt(HOLDER, row.holderNameCipher()), row.businessNumberCipher() == null ? null : cipher.decrypt(BUSINESS_NUMBER, row.businessNumberCipher()),
                now.plus(Duration.ofMinutes(REVEAL_MINUTES)));
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    /** 본인인증 이름(평문 — 이 서비스 안에서만) — 미인증·이름 없음·데이터 키 없음이면 비어 있다 */
    private Optional<String> identityName(long userId) {
        if (!cipher.isConfigured()) {
            return Optional.empty();
        }
        return identities.findNameCipher(userId).map(c -> cipher.decrypt(IdentityVerificationService.NAME, c));
    }

    private static EmoticonStudioView.PayoutAccount view(PayoutRow r, String identityMasked) {
        return new EmoticonStudioView.PayoutAccount(true, r.payeeType(), r.businessNumberMasked(), r.bankCode(), "****" + r.accountLast4(), r.holderMasked(), identityMasked,
                r.updatedAt());
    }

    /** {@code 123-45-*****} */
    static String maskBizNo(String digits) {
        return digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-*****";
    }

    private static String compact(String name) {
        return name.replaceAll("\\s+", "");
    }
}
