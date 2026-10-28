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
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매 스위치·운영 체크리스트·사업자 정보(commerce.md 20절, 공구 계약 결정 18·56).
 *
 * <ul>
 *   <li><b>켜기</b>: 최고 관리자만(403 {@code COMMERCE_SALES_ROOT_ONLY}), 자동 조건(사업자 정보 필수 칸 · PG 설정 · 켜진 택배사 · 본인인증 설정) + 체크 항목 여섯이 모두 차야 한다
 *       (409 {@code COMMERCE_SALES_NOT_READY} + {@code missing}). <b>끄기</b>는 최고 관리자 또는 {@code commerce.settings}(컨트롤러가 막는다) — 새 주문만 막고 진행 중 주문은 그대로.</li>
 *   <li>켜기·끄기 모두 사유 5~200자, 감사 {@code COMMERCE_SALES_TOGGLE}(돈 관련, 전→후·사유) + 운영자 알림 {@code COMMERCE_SALES_TOGGLE}.</li>
 *   <li>판매 여부는 그 트랜잭션 안에서 DB 의 지금 값을 읽는다(결정 19 — 캐시 없음). 회원 공개 정보({@link #publicInfo})는 스위치가 켜져 있고 필수 칸이 찼을 때만 사업자 정보를 싣는다.</li>
 *   <li>local 은 시드({@code GroupBuyLocalSeeder})가 조건을 실제로 채우고 켠다 — 「local 이면 검사 생략」 코드는 없다.</li>
 * </ul>
 */
@Service
public class CommerceSalesService {

    /** 자동 조건 키(화면 {@code SalesAutoCheckKey} 차례) */
    public static final List<String> AUTO_CHECKS = List.of("BUSINESS_INFO", "PG_KEYS", "CARRIER", "IDENTITY");

    private static final Pattern BUSINESS_NO = Pattern.compile("^[0-9]{3}-[0-9]{2}-[0-9]{5}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
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
        List<AutoCheck> auto = List.of(new AutoCheck("BUSINESS_INFO", store.businessInfo().ready()), new AutoCheck("PG_KEYS", pgReady()),
                new AutoCheck("CARRIER", store.anyActiveCarrier()), new AutoCheck("IDENTITY", identityReady()));
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
     * 사업자 정보 저장(통째로) — 글자 수·전자우편 모양·등록번호(모양 + 체크섬). 빈 칸은 저장한다(판매 켜기 조건만 막힌다). 바뀐 칸 이름만 감사(일반).
     */
    @Transactional
    public BusinessInfo saveBusinessInfo(BusinessInfo in, long operatorId, String ip) {
        BusinessInfo before = store.businessInfo();
        BusinessInfo v = new BusinessInfo(text("companyName", in.companyName(), 100), text("representative", in.representative(), 50), businessNo(in.businessNo()),
                text("mailOrderNo", in.mailOrderNo(), 50), text("mailOrderAuthority", in.mailOrderAuthority(), 50), text("address", in.address(), 300),
                text("phone", in.phone(), 30), email(in.email()), text("businessHours", in.businessHours(), 100), text("privacyOfficer", in.privacyOfficer(), 100),
                text("hostingProvider", in.hostingProvider(), 100), text("shippingRefundPolicy", in.shippingRefundPolicy(), 3000), null, null);
        store.saveBusinessInfo(v, operatorId, now());
        List<String> changed = new ArrayList<>();
        diff(changed, "companyName", before.companyName(), v.companyName());
        diff(changed, "representative", before.representative(), v.representative());
        diff(changed, "businessNo", before.businessNo(), v.businessNo());
        diff(changed, "mailOrderNo", before.mailOrderNo(), v.mailOrderNo());
        diff(changed, "mailOrderAuthority", before.mailOrderAuthority(), v.mailOrderAuthority());
        diff(changed, "address", before.address(), v.address());
        diff(changed, "phone", before.phone(), v.phone());
        diff(changed, "email", before.email(), v.email());
        diff(changed, "businessHours", before.businessHours(), v.businessHours());
        diff(changed, "privacyOfficer", before.privacyOfficer(), v.privacyOfficer());
        diff(changed, "hostingProvider", before.hostingProvider(), v.hostingProvider());
        diff(changed, "shippingRefundPolicy", before.shippingRefundPolicy(), v.shippingRefundPolicy());
        if (!changed.isEmpty()) {
            record(operatorId, AuditActions.COMMERCE_BUSINESS_INFO_UPDATE, "business-info", ip, Map.of("fields", changed));
        }
        return store.businessInfo();
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

    private static String businessNo(String value) {
        String v = text("businessNo", value, 12);
        if (!v.isEmpty() && !validBusinessNo(v)) {
            throw ValidationException.of("values.businessNo", new MessageRef("commerce.business.businessNo.invalid"));
        }
        return v;
    }

    private static String email(String value) {
        String v = text("email", value, 200);
        if (!v.isEmpty() && !EMAIL.matcher(v).matches()) {
            throw ValidationException.of("values.email", new MessageRef("commerce.business.email.invalid"));
        }
        return v;
    }
}
