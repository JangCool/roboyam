package com.community.groupbuy.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.groupbuy.entity.ReturnFeeMode;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.ShippingPolicyStore;
import com.community.groupbuy.repository.master.ShippingPolicyStore.PolicyRow;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배송비 정책과 예약 변경(commerce.md 10-2) — 공구에 배송비를 직접 넣지 않고 정책 이름(code)의 기간 줄에서 고른다. 인상이 예정되면 <b>적용 시작 시각을 정해 새 줄</b>을 미리 넣고,
 * 그 시각부터 새로 모집을 시작하는 공구가 그 값을 복사한다(진행 중 공구는 시작 때 값 그대로 — P16e).
 *
 * <ul>
 *   <li>적용이 시작된 줄은 고치거나 지우지 않는다(DB 트리거). 새 줄을 넣으면 그 시각에 유효하던 줄의 끝을 그 시각으로 닫는다(끝만 미래로 — 트리거가 허용하는 유일한 변경).</li>
 *   <li>같은 code 의 기간 겹침은 DB 배제 제약이 막는다. 새 줄과 같거나 늦게 시작하는 줄이 이미 있으면 거절(그 예약을 먼저 정리) — 409 {@code SHIPPING_POLICY_CONFLICT}.</li>
 *   <li>감사 {@code COMMERCE_SHIPPING_POLICY_CREATE}(돈 관련 — 전·후). 관리 권한 {@code commerce.settings} 는 컨트롤러(2b-4)가 본다.</li>
 * </ul>
 */
@Service
public class ShippingPolicyService {

    public static final int NAME_MAX = 60;

    /** 새 정책 줄 — {@code effectiveFrom} 이 비면 지금 */
    public record PolicyInput(String code, String name, long baseFee, Long freeShippingOver, long returnFee, String returnFeeMode, Instant effectiveFrom) {
    }

    private final ShippingPolicyStore store;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public ShippingPolicyService(ShippingPolicyStore store, AuditLogService audit, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.audit = audit;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 그 시각에 유효한 줄 */
    public Optional<PolicyRow> effective(String code, Instant at) {
        return store.effective(code, at);
    }

    public List<PolicyRow> history(String code) {
        return store.history(code);
    }

    /**
     * 새 줄을 넣는다. 시작 시각은 지금보다 앞일 수 없다(지난 공구의 사본이 바뀌지 않게).
     *
     * @return 새 줄 ID
     * @throws ValidationException 칸 형식
     * @throws GroupBuyException   {@code SHIPPING_POLICY_CONFLICT} — 지난 시각이거나 같은·늦은 예약이 이미 있다
     */
    @Transactional
    public long schedule(PolicyInput input, Long operatorId, String ip) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        validate(input);
        Instant from = input.effectiveFrom() == null ? now : input.effectiveFrom().truncatedTo(ChronoUnit.MICROS);
        if (from.isBefore(now)) {
            throw GroupBuyException.shippingPolicyConflict();
        }
        store.lockCode(input.code());
        if (store.history(input.code()).stream().anyMatch(r -> !r.effectiveFrom().isBefore(from))) {
            throw GroupBuyException.shippingPolicyConflict();
        }
        Optional<PolicyRow> previous = store.effective(input.code(), from);
        previous.ifPresent(row -> store.close(row.id(), from));
        long id = ids.nextId();
        String mode = input.returnFeeMode() == null ? ReturnFeeMode.ONE_WAY.name() : input.returnFeeMode();
        store.insert(id, input.code(), input.name().strip(), input.baseFee(), input.freeShippingOver(), input.returnFee(), mode, from, null, operatorId, now);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("code", input.code());
        detail.put("effectiveFrom", from.toString());
        previous.ifPresent(row -> detail.put("before", fees(row.baseFee(), row.freeShippingOver(), row.returnFee(), row.returnFeeMode())));
        detail.put("after", fees(input.baseFee(), input.freeShippingOver(), input.returnFee(), mode));
        audit.record(new AuditEvent(operatorId == null ? AuditActorType.SYSTEM : AuditActorType.OPERATOR, operatorId, AuditActions.COMMERCE_SHIPPING_POLICY_CREATE,
                AuditResult.SUCCESS, "SHIPPING_POLICY", Long.toString(id), ip, detail));
        return id;
    }

    private static Map<String, Object> fees(long baseFee, Long freeShippingOver, long returnFee, String mode) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("baseFee", baseFee);
        out.put("freeShippingOver", freeShippingOver == null ? "" : freeShippingOver.toString());
        out.put("returnFee", returnFee);
        out.put("returnFeeMode", mode);
        return out;
    }

    private static void validate(PolicyInput input) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        if (input.code() == null || !input.code().matches("[A-Z][A-Z0-9_]{1,29}")) {
            fields.put("code", new MessageRef("groupbuy.shipping.code.invalid"));
        }
        if (input.name() == null || input.name().isBlank() || input.name().strip().length() > NAME_MAX) {
            fields.put("name", new MessageRef("groupbuy.shipping.name.invalid"));
        }
        if (input.baseFee() < 0 || input.returnFee() < 0 || (input.freeShippingOver() != null && input.freeShippingOver() <= 0)) {
            fields.put("baseFee", new MessageRef("groupbuy.shipping.fee.invalid"));
        }
        if (input.returnFeeMode() != null && !input.returnFeeMode().equals(ReturnFeeMode.ONE_WAY.name()) && !input.returnFeeMode().equals(ReturnFeeMode.ROUND_TRIP.name())) {
            fields.put("returnFeeMode", new MessageRef("groupbuy.shipping.mode.invalid"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
    }
}
