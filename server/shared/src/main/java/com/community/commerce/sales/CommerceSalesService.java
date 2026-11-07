package com.community.commerce.sales;

import com.community.audit.dto.AuditActions;
import com.community.audit.entity.AuditActorType;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.identity.provider.IdentityVerifier;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.payment.gateway.PaymentGateway;
import com.community.site.entity.SiteOperatorStage;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매 스위치·운영 체크리스트·사업자 정보(commerce.md 20절, 공구 계약 결정 18·56).
 *
 * <ul>
 *   <li><b>사업자 정보의 정본은 운영 주체 설정</b>({@code site_operator} — terms-operator-contract): 겹치는 칸은 거기서 읽고, 여기서는 커머스 고유 칸만 저장한다.
 *       판매가 켜져 있는 동안 운영 주체를 개인 단계로 바꾸거나 통신판매업 신고번호를 비울 수 없다({@link #assertOperatorChangeAllowed}, 409 {@code COMMERCE_SALES_OPEN}).</li>
 *   <li><b>켜기</b>: 최고 관리자만(403 {@code COMMERCE_SALES_ROOT_ONLY}), 자동 조건(사업자 정보 필수 칸 · 운영 주체 단계(개인사업자 이상 + 통신판매업 신고번호) · PG 설정 · 켜진 택배사 ·
 *       본인인증 설정) + 체크 항목 여섯이 모두 차야 한다
 *       (409 {@code COMMERCE_SALES_NOT_READY} + {@code missing}). <b>끄기</b>는 최고 관리자 또는 {@code commerce.settings}(컨트롤러가 막는다) — 새 주문만 막고 진행 중 주문은 그대로.</li>
 *   <li>켜기·끄기 모두 사유 5~200자, 감사 {@code COMMERCE_SALES_TOGGLE}(돈 관련, 전→후·사유) + 운영자 알림 {@code COMMERCE_SALES_TOGGLE}.</li>
 *   <li>판매 여부는 그 트랜잭션 안에서 DB 의 지금 값을 읽는다(결정 19 — 캐시 없음). 회원 공개 정보({@link #publicInfo})는 스위치가 켜져 있고 필수 칸이 찼을 때만 사업자 정보를 싣는다.</li>
 *   <li>local 은 시드({@code GroupBuyLocalSeeder})가 조건을 실제로 채우고 켠다 — 「local 이면 검사 생략」 코드는 없다.</li>
 * </ul>
 */
@Service
public class CommerceSalesService {

    /**
     * 자동 조건 키(화면 {@code SalesAutoCheckKey} 차례). {@code OPERATOR_STAGE} = 운영 주체가 개인사업자 이상이고 통신판매업 신고번호가 있다(terms-operator-contract 세션 정리 결정 —
     * 사업자 정보의 정본은 운영 주체 설정).
     */
    public static final List<String> AUTO_CHECKS = List.of("BUSINESS_INFO", "OPERATOR_STAGE", "PG_KEYS", "CARRIER", "IDENTITY");

    private static final Pattern BUSINESS_NO = Pattern.compile("^[0-9]{3}-[0-9]{2}-[0-9]{5}$");
    private static final int[] WEIGHTS = {1, 3, 7, 1, 3, 7, 1, 3, 5};

    /** 판매 스위치 화면 한 벌 */
    public record SalesState(boolean enabled, List<AutoCheck> autoChecks, List<CommerceSalesStore.ChecklistRow> checklist, boolean canEnable, Instant changedAt,
                             String changedBy) {

        /** 켜는 데 모자란 키(자동 조건 → 체크 항목) */
        public List<String> missing() {
            List<String> out = new ArrayList<>();
            autoChecks.stream().filter(c -> !c.ok()).forEach(c -> out.add(c.key()));
            checklist.stream().filter(c -> !c.checked()).forEach(c -> out.add(c.key()));
            return out;
        }
    }

    public record AutoCheck(String key, boolean ok) {
    }

    /** 회원 공개 정보({@code GET /api/commerce/info}) — {@code business} 는 판매가 켜져 있고 필수 칸이 찼을 때만 */
    public record PublicInfo(boolean salesOpen, boolean ready, BusinessInfo business) {
    }

    private final CommerceSalesStore store;
    private final AuditLogService audit;
    private final NotificationPublisher notices;
    private final ObjectProvider<PaymentGateway> gateways;
    private final ObjectProvider<IdentityVerifier> verifiers;
    private final ObjectProvider<Clock> clocks;

    public CommerceSalesService(CommerceSalesStore store, AuditLogService audit, NotificationPublisher notices, ObjectProvider<PaymentGateway> gateways,
                                ObjectProvider<IdentityVerifier> verifiers, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.audit = audit;
        this.notices = notices;
        this.gateways = gateways;
        this.verifiers = verifiers;
        this.clocks = clocks;
    }

    /** 공구 판매가 켜져 있나 — 그 트랜잭션 안의 DB 값(결정 19) */
    public boolean groupBuyOpen() {
        return store.groupBuyOpen();
    }

    @Transactional(readOnly = true)
    public SalesState state() {
        return state(store.switchRow());
    }

    private SalesState state(CommerceSalesStore.SwitchRow row) {
        List<AutoCheck> auto = List.of(new AutoCheck("BUSINESS_INFO", store.businessInfo().ready()), new AutoCheck("OPERATOR_STAGE", store.operatorCanSell()),
                new AutoCheck("PG_KEYS", pgReady()), new AutoCheck("CARRIER", store.anyActiveCarrier()), new AutoCheck("IDENTITY", identityReady()));
        List<CommerceSalesStore.ChecklistRow> checklist = store.checklist();
        boolean can = auto.stream().allMatch(AutoCheck::ok) && checklist.stream().allMatch(CommerceSalesStore.ChecklistRow::checked);
        return new SalesState(row.groupBuyOpen(), auto, checklist, can, row.changedAt(), row.changedBy());
    }

    /**
     * 켜기·끄기.
     *
     * @param root 최고 관리자인가(켜기는 최고 관리자만 — 끄기 권한은 컨트롤러가 본다)
     */
    @Transactional
    public SalesState toggle(boolean enabled, String reason, long operatorId, boolean root, String ip) {
        String why = reason(reason);
        if (enabled && !root) {
            throw CommerceSalesException.rootOnly();
        }
        boolean before = store.lockSwitch();
        if (enabled) {
            SalesState now = state(store.switchRow());
            if (!now.canEnable()) {
                throw CommerceSalesException.notReady(now.missing());
            }
        }
        if (before != enabled) {
            store.setSwitch(enabled, why, operatorId, now());
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("before", before);
            detail.put("after", enabled);
            detail.put("reason", why);
            record(operatorId, AuditActions.COMMERCE_SALES_TOGGLE, "sales", ip, detail);
            notices.publish(AdminNotificationEvent.COMMERCE_SALES_TOGGLE, NotificationContext.empty().actor(operatorId));
        }
        return state(store.switchRow());
    }

    /** 운영 체크 항목 체크·해제 — 바뀌었을 때만 감사(일반). 끄면 켜진 판매는 그대로(다음 켜기만 막힌다) */
    @Transactional
    public SalesState check(String key, boolean checked, long operatorId, String ip) {
        if (store.checklist().stream().noneMatch(c -> c.key().equals(key))) {
            throw ValidationException.of("key", new MessageRef("commerce.sales.checklist.unknown"));
        }
        if (store.check(key, checked, operatorId, now())) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("key", key);
            detail.put("checked", checked);
            record(operatorId, AuditActions.COMMERCE_SALES_CHECKLIST, "sales", ip, detail);
        }
        return state(store.switchRow());
    }

    @Transactional(readOnly = true)
    public BusinessInfo businessInfo() {
        return store.businessInfo();
    }

    /**
     * 사업자 정보 저장 — <b>커머스 고유 칸(운영시간 {@code businessHours} · 배송·환불 안내 {@code shippingRefundPolicy})만</b> 저장한다. 겹치는 칸(상호·대표자·등록번호·통신판매업
     * 신고번호·신고 기관·주소·전화·이메일·보호책임자·호스팅)은 정본인 운영 주체 설정({@code PUT /api/admin/site/operator})이 저장하므로 <b>받아도 무시한다</b>(검사도 하지 않는다 —
     * terms-operator-contract 세션 정리 결정). 글자 수만 본다. 바뀐 칸 이름만 감사(일반). 돌려주는 값의 겹치는 칸은 운영 주체 값이다.
     */
    @Transactional
    public BusinessInfo saveBusinessInfo(BusinessInfo in, long operatorId, String ip) {
        BusinessInfo before = store.businessInfo();
        String hours = text("businessHours", in.businessHours(), 100);
        String policy = text("shippingRefundPolicy", in.shippingRefundPolicy(), 3000);
        BusinessInfo v = new BusinessInfo(before.companyName(), before.representative(), before.businessNo(), before.mailOrderNo(), before.mailOrderAuthority(),
                before.address(), before.phone(), before.email(), hours, before.privacyOfficer(), before.hostingProvider(), policy, null, null);
        store.saveBusinessInfo(v, operatorId, now());
        List<String> changed = new ArrayList<>();
        diff(changed, "businessHours", before.businessHours(), v.businessHours());
        diff(changed, "shippingRefundPolicy", before.shippingRefundPolicy(), v.shippingRefundPolicy());
        if (!changed.isEmpty()) {
            record(operatorId, AuditActions.COMMERCE_BUSINESS_INFO_UPDATE, "business-info", ip, Map.of("fields", changed));
        }
        return store.businessInfo();
    }

    /**
     * 운영 주체 저장 전 검사 — 판매가 켜져 있는 동안 운영 주체를 개인 단계로 바꾸거나 통신판매업 신고번호를 비우는 저장은 막는다(409 {@code COMMERCE_SALES_OPEN} — 먼저 판매를 끈다).
     * 판매 스위치 행을 잠가 켜기({@link #toggle})와 줄 세운다 — 운영 주체 저장 트랜잭션 안에서, 운영 주체 행보다 먼저 부른다(잠그는 차례: 스위치 → 운영 주체, 켜기와 같다).
     * <b>로보티콘 유료 판매</b>({@code emoticon_settings.paid_sales_enabled})가 켜져 있어도 같은 저장을 막는다(409 {@code EMOTICON_PAID_SALES_OPEN} — 먼저 로보티콘 설정에서 끈다.
     * 공구가 켜져 있으면 공구 코드가 먼저). 광고·핫딜 유료 신청은 스위치가 없어 막지 않는다(그때그때 {@link CommerceSalesStore#operatorCanSell} 로 판정).
     *
     * @param nextStage           저장할 단계
     * @param nextMailOrderNumber 저장할 통신판매업 신고번호(개인이면 null)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void assertOperatorChangeAllowed(SiteOperatorStage nextStage, String nextMailOrderNumber) {
        boolean open = store.lockSwitch();
        boolean cannotSell = !nextStage.business() || nextMailOrderNumber == null || nextMailOrderNumber.isBlank();
        if (!cannotSell) {
            return;
        }
        if (open) {
            throw CommerceSalesException.salesOpen();
        }
        // 로보티콘 유료 판매도 같은 조건(사용자 결정 2026-11-07) — 그 스위치 행을 나눔 잠금으로 읽는다(설정 저장의 켜기와 줄 선다)
        if (store.lockEmoticonPaidSales()) {
            throw CommerceSalesException.emoticonPaidSalesOpen();
        }
    }

    /** 회원 공개 정보 — 스위치 꺼짐이면 사업자 정보를 싣지 않는다(G41) */
    @Transactional(readOnly = true)
    public PublicInfo publicInfo() {
        boolean open = store.groupBuyOpen();
        BusinessInfo info = store.businessInfo();
        boolean ready = info.ready();
        return new PublicInfo(open, ready, open && ready ? info : null);
    }

    /** 사업자등록번호 체크섬(국세청 규칙 — 가중치 1·3·7·1·3·7·1·3·5, 아홉째 자리 ×5 의 십의 자리를 더한다) */
    public static boolean validBusinessNo(String value) {
        if (value == null || !BUSINESS_NO.matcher(value).matches()) {
            return false;
        }
        String digits = value.replace("-", "");
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (digits.charAt(i) - '0') * WEIGHTS[i];
        }
        sum += ((digits.charAt(8) - '0') * 5) / 10;
        return (10 - sum % 10) % 10 == digits.charAt(9) - '0';
    }

    private boolean pgReady() {
        PaymentGateway gateway = gateways.getIfAvailable();
        return gateway != null && gateway.ready();
    }

    private boolean identityReady() {
        IdentityVerifier verifier = verifiers.getIfAvailable();
        if (verifier == null) {
            return false;
        }
        try {
            verifier.startInfo();
            return true;
        } catch (RuntimeException e) {
            return false; // 설정 칸이 비었다(503) — 이유는 화면이 조건 키로 보인다
        }
    }

    private void record(long operatorId, String action, String targetId, String ip, Map<String, Object> detail) {
        audit.record(new AuditEvent(AuditActorType.OPERATOR, operatorId, action, AuditResult.SUCCESS, "COMMERCE_SETTINGS", targetId, ip, detail));
    }

    private Instant now() {
        return clocks.getIfUnique(Clock::systemUTC).instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static void diff(List<String> changed, String name, String before, String after) {
        if (!Objects.equals(before, after)) {
            changed.add(name);
        }
    }

    private static String reason(String reason) {
        if (reason == null || reason.strip().length() < 5 || reason.strip().length() > 200) {
            throw ValidationException.of("reason", new MessageRef("groupbuy.reason.invalid"));
        }
        return reason.strip();
    }

    private static String text(String field, String value, int max) {
        String v = value == null ? "" : value.strip();
        if (v.length() > max) {
            throw ValidationException.of("values." + field, new MessageRef("commerce.business.tooLong", max));
        }
        return v;
    }
}
