package com.community.groupbuy.service;

import com.community.audit.dto.AuditActions;
import com.community.groupbuy.dto.OrderLine;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.RefundCause;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyRefundStore;
import com.community.groupbuy.repository.master.GroupBuyRefundStore.PendingAdminRefund;
import com.community.groupbuy.repository.master.GroupBuyRequestStore;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.repository.master.ParticipationStore;
import com.community.groupbuy.repository.master.ParticipationStore.ItemRow;
import com.community.groupbuy.repository.master.ParticipationStore.ParticipationRow;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 운영 환불(요청 없이 — 계약 결정 32·53·70·71·73, {@code commerce.refund.approve} · 고액 {@code commerce.refund.large} — 컨트롤러).
 *
 * <ul>
 *   <li><b>발송 전 주문</b>(JOINED·CONFIRMED·PREPARING): 「발송 전 취소」와 같다 — 줄·수량을 지정(비면 전체), CANCEL 이동, 일부면 배송비 재계산(사업자 귀책이면 없음).</li>
 *   <li><b>배송완료 주문</b>: 금액만 돌려준다(회수 없음 — 보상·가격 조정). 수량 이동 없이 환불 누계만, 남은 결제액을 다 쓰면 REFUNDED. 줄별 금액은 남은 상품 금액 비례(원 미만 버림, 남는 원은 마지막 줄 — 결정 71).</li>
 *   <li>배송 중 주문은 409 {@code GROUP_BUY_REFUND_SHIPPED}, 끝난 주문(REFUNDED·CANCELED)은 409 {@code ORDER_STATE}, 열린 요청이 있으면 409 {@code REQUEST_OPEN}, 열린 대사 이슈면 409 {@code PAYMENT_UNRECONCILED}.</li>
 *   <li><b>고액 문턱</b>: 운영자가 금액을 정하는 환불이라 그 주문의 운영자 금액 누계(이번 포함)가 문턱(설정, 0 = 끔) 이상이면 고액 권한이 필요 — 없으면 승인 대기로 올린다(쪼개서 피하지 못하게 누계로).</li>
 * </ul>
 */
@Service
public class GroupBuyAdminRefundService {

    /** 결과 — QUEUED(환불을 넣었다) · AWAITING_APPROVAL(고액 권한자 승인 대기) */
    public record Result(String state, Long refundId, Long pendingId, long amount) {
    }

    private final ParticipationStore participations;
    private final GroupBuyStore groups;
    private final GroupBuyRefundStore refundStore;
    private final GroupBuyRequestStore requests;
    private final GroupBuyRefundService refunds;
    private final ParticipationCancelService cancels;
    private final GroupBuyAudit audit;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;
    private final Clock clock;

    public GroupBuyAdminRefundService(ParticipationStore participations, GroupBuyStore groups, GroupBuyRefundStore refundStore, GroupBuyRequestStore requests,
                                      GroupBuyRefundService refunds, ParticipationCancelService cancels, GroupBuyAudit audit, SnowflakeIdGenerator ids,
                                      PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
        this.participations = participations;
        this.groups = groups;
        this.refundStore = refundStore;
        this.requests = requests;
        this.refunds = refunds;
        this.cancels = cancels;
        this.audit = audit;
        this.ids = ids;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /**
     * 운영 환불.
     *
     * @param lines        발송 전 주문의 줄·수량(비면 전체) — 배송완료 주문에는 쓰지 않는다
     * @param amount       배송완료 주문의 금액(1 ~ 남은 결제액) — 발송 전 주문에는 쓰지 않는다(서버가 계산)
     * @param sellerFault  사업자 귀책(재부과·반품비를 적용하지 않는다 — 결정 53)
     * @param largeAllowed 부르는 운영자가 고액 권한을 가졌는가
     */
    public Result refund(long participationId, List<OrderLine> lines, Long amount, boolean sellerFault, String reason, boolean largeAllowed, long operatorId, String ip) {
        String why = GroupBuyAudit.reason(reason);
        try {
            return tx.execute(status -> {
                ParticipationRow p = participations.lock(participationId).orElseThrow(GroupBuyException::orderNotFound);
                Plan plan = plan(p, lines, amount, sellerFault);
                long threshold = groups.settings().largeRefundThreshold();
                if (threshold > 0 && !largeAllowed && refundStore.operatorAmountTotal(p.id()) + plan.amount() >= threshold) {
                    long pendingId = ids.nextId();
                    refundStore.insertPending(pendingId, p.id(), plan.kind(), plan.amount(), plan.linesJson(), sellerFault, why, operatorId, now());
                    return new Result("AWAITING_APPROVAL", null, pendingId, plan.amount());
                }
                long refundId = execute(p, plan, sellerFault, why, operatorId, ip);
                return new Result("QUEUED", refundId, null, plan.amount());
            });
        } catch (DuplicateKeyException e) {
            throw GroupBuyException.requestOpen(); // 같은 주문에 승인 대기가 이미 있다
        }
    }

    /** 운영 환불 미리 보기(2b-4 — 화면 확인 창의 숫자) */
    public record Preview(long itemRefund, long shippingRefund, long reshippingFee, long total, long remainingAfter, long adminTotalAfter, long threshold,
                          boolean needsLarge) {
    }

    /**
     * 운영 환불 미리 보기 — 막는 조건(배송 중·열린 요청·대사 이슈·남은 결제액)과 계산은 {@link #refund} 와 같고 아무것도 바꾸지 않는다.
     *
     * @param largeAllowed 부르는 운영자가 고액 권한을 가졌는가(없고 누계가 문턱에 닿으면 {@code needsLarge})
     */
    public Preview preview(long participationId, List<OrderLine> lines, Long amount, boolean sellerFault, boolean largeAllowed) {
        ParticipationRow p = participations.find(participationId).orElseThrow(GroupBuyException::orderNotFound);
        Plan plan = plan(p, lines, amount, sellerFault);
        long item = plan.amount();
        long shipping = 0;
        long reshipping = 0;
        if ("PRE_SHIPPING".equals(plan.kind())) {
            GroupBuyRow g = groups.find(p.groupBuyId()).orElseThrow();
            List<ItemRow> items = participations.items(p.id());
            List<GroupBuyRefundCalculator.Held> held = items.stream().map(i -> new GroupBuyRefundCalculator.Held(i.id(), i.skuId(), i.main(), i.unitPrice(), i.heldQty(), 0))
                    .toList();
            List<GroupBuyRefundCalculator.Line> calcLines = plan.lines().entrySet().stream().filter(e -> e.getValue() > 0)
                    .map(e -> new GroupBuyRefundCalculator.Line(e.getKey(), e.getValue(), null)).toList();
            GroupBuyRefundCalculator.Result r = GroupBuyRefundCalculator.cancel(GroupBuyRefundCalculator.order(p), GroupBuyRefundCalculator.policy(g), held, calcLines,
                    !sellerFault);
            item = r.itemRefund();
            shipping = r.shippingRefund() + r.reshippingRefund();
            reshipping = r.reshippingFee();
        }
        long threshold = groups.settings().largeRefundThreshold();
        long after = refundStore.operatorAmountTotal(p.id()) + plan.amount();
        return new Preview(item, shipping, reshipping, plan.amount(), p.remainingCash() - plan.amount(), after, threshold, threshold > 0 && !largeAllowed && after >= threshold);
    }

    /** 승인 대기 승인(고액 권한자) — 그때의 상태로 다시 확인하고 넣는다 */
    public Result approvePending(long pendingId, long operatorId, String ip) {
        return tx.execute(status -> {
            PendingAdminRefund found = refundStore.lockPending(pendingId).orElseThrow(GroupBuyException::requestNotFound);
            ParticipationRow p = participations.lock(found.participationId()).orElseThrow();
            PendingAdminRefund pending = refundStore.lockPending(pendingId).orElseThrow();
            if (!"AWAITING_APPROVAL".equals(pending.status())) {
                throw GroupBuyException.requestNotOpen();
            }
            Plan plan = "AMOUNT_ONLY".equals(pending.kind()) ? plan(p, null, pending.amount(), pending.sellerFault())
                    : plan(p, linesFromJson(pending.linesJson()), null, pending.sellerFault());
            if (plan.amount() != pending.amount()) {
                throw GroupBuyException.refundAmountChanged(pending.amount(), plan.amount());
            }
            long refundId = execute(p, plan, pending.sellerFault(), pending.reason(), operatorId, ip);
            refundStore.decidePending(pendingId, true, refundId, operatorId, now());
            return new Result("QUEUED", refundId, pendingId, plan.amount());
        });
    }

    public void rejectPending(long pendingId, long operatorId) {
        tx.executeWithoutResult(status -> {
            if (!refundStore.decidePending(pendingId, false, null, operatorId, now())) {
                throw GroupBuyException.requestNotOpen();
            }
        });
    }

    private record Plan(String kind, long amount, Map<Long, Integer> lines, String linesJson) {
    }

    private Plan plan(ParticipationRow p, List<OrderLine> lines, Long amount, boolean sellerFault) {
        if (p.status() == ParticipationStatus.SHIPPED) {
            throw GroupBuyException.refundShipped();
        }
        boolean preShipping = ParticipationStatus.PAID_BEFORE_SHIPPING.contains(p.status());
        if (!preShipping && p.status() != ParticipationStatus.DELIVERED) {
            throw GroupBuyException.orderState();
        }
        if (requests.hasOpen(p.id())) {
            throw GroupBuyException.requestOpen();
        }
        if (refundStore.paymentUnreconciled(p.paymentOrderId())) {
            throw GroupBuyException.paymentUnreconciled();
        }
        List<ItemRow> items = participations.items(p.id());
        if (preShipping) {
            Map<Long, Integer> plan = new LinkedHashMap<>();
            StringJoiner json = new StringJoiner(",", "[", "]");
            if (lines == null || lines.isEmpty()) {
                plan.putAll(ParticipationCancelService.wholeOrder(items));
            } else {
                for (OrderLine line : lines) {
                    ItemRow item = items.stream().filter(i -> i.skuId() == line.skuId()).findFirst().orElse(null);
                    if (item == null || line.qty() < 1 || line.qty() > item.heldQty() || plan.containsKey(item.id())) {
                        throw ValidationException.of("items", new MessageRef("groupbuy.cancel.lines.invalid"));
                    }
                    plan.put(item.id(), line.qty());
                }
            }
            items.stream().filter(i -> plan.getOrDefault(i.id(), 0) > 0).forEach(i -> json.add("{\"skuId\":\"" + i.skuId() + "\",\"qty\":" + plan.get(i.id()) + "}"));
            GroupBuyRow g = groups.find(p.groupBuyId()).orElseThrow();
            List<GroupBuyRefundCalculator.Held> held = items.stream().map(i -> new GroupBuyRefundCalculator.Held(i.id(), i.skuId(), i.main(), i.unitPrice(), i.heldQty(), 0))
                    .toList();
            List<GroupBuyRefundCalculator.Line> calcLines = plan.entrySet().stream().filter(e -> e.getValue() > 0)
                    .map(e -> new GroupBuyRefundCalculator.Line(e.getKey(), e.getValue(), null)).toList();
            long total = GroupBuyRefundCalculator.cancel(GroupBuyRefundCalculator.order(p), GroupBuyRefundCalculator.policy(g), held, calcLines, !sellerFault).total();
            return new Plan("PRE_SHIPPING", total, plan, json.toString());
        }
        if (amount == null || amount < 1 || amount > p.remainingCash()) {
            throw ValidationException.of("amount", new MessageRef("groupbuy.refund.amount.invalid"));
        }
        // 금액만 — 남은 상품 금액 비례로 줄에 나눈다(원 미만 버림, 남는 원은 마지막 줄 — 결정 71)
        List<ItemRow> heldItems = items.stream().filter(i -> i.heldQty() > 0).toList();
        long base = heldItems.stream().mapToLong(i -> i.unitPrice() * i.heldQty()).sum();
        StringJoiner json = new StringJoiner(",", "[", "]");
        long given = 0;
        for (int k = 0; k < heldItems.size(); k++) {
            ItemRow i = heldItems.get(k);
            long share = k == heldItems.size() - 1 ? amount - given : base == 0 ? 0 : amount * (i.unitPrice() * i.heldQty()) / base;
            given += share;
            json.add("{\"itemId\":\"" + i.id() + "\",\"skuId\":\"" + i.skuId() + "\",\"amount\":" + share + ",\"amountOnly\":true}");
        }
        return new Plan("AMOUNT_ONLY", amount, Map.of(), json.toString());
    }

    private long execute(ParticipationRow p, Plan plan, boolean sellerFault, String why, long operatorId, String ip) {
        Instant now = now();
        long refundId;
        if ("PRE_SHIPPING".equals(plan.kind())) {
            cancels.cancelLines(p, plan.lines(), RefundCause.ADMIN, !sellerFault, new GroupBuyRefundService.Extra(null, 0, plan.amount()), operatorId, why);
            refundId = refundStore.ofParticipation(p.id()).getLast().id();
        } else {
            refundId = refunds.refund(p, RefundCause.ADMIN, new GroupBuyRefundService.Parts(plan.amount(), 0, 0, 0), new GroupBuyRefundService.Extra(null, 0, plan.amount()),
                    plan.linesJson(), operatorId, why, now);
            if (p.remainingCash() - plan.amount() == 0) {
                // 운영 환불 누계가 남은 결제액을 모두 썼다 — REFUNDED(결정 46)
                participations.transition(p.id(), EnumSet.of(ParticipationStatus.DELIVERED), ParticipationStatus.REFUNDED, now);
                participations.insertHistory(ids.nextId(), p, ParticipationStatus.DELIVERED, ParticipationStatus.REFUNDED, "ADMIN_REFUND", operatorId, now);
            }
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("kind", plan.kind());
        detail.put("amount", plan.amount());
        detail.put("reason", why);
        detail.put("sellerFault", sellerFault);
        audit.record(operatorId, AuditActions.GROUPBUY_REFUND, GroupBuyAudit.TARGET_ORDER, p.id(), ip, detail);
        return refundId;
    }

    private static final tools.jackson.databind.json.JsonMapper JSON = tools.jackson.databind.json.JsonMapper.builder().build();

    /** 승인 대기에 남긴 줄(jsonb — 글자 차례는 DB 가 바꾼다) */
    private static List<OrderLine> linesFromJson(String json) {
        List<OrderLine> out = new java.util.ArrayList<>();
        if (json != null) {
            JSON.readTree(json).forEach(n -> out.add(new OrderLine(Long.parseLong(n.get("skuId").asString()), n.get("qty").asInt())));
        }
        return out;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
