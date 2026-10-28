package com.community.groupbuy.service;

import com.community.groupbuy.dto.OrderLine;
import com.community.groupbuy.entity.Fulfillment;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.RefundCause;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.entity.RequestStatus;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 결제된 주문의 발송 전 취소(commerce.md 10-3, 계약 결정 29·42·45·46·52·61·69) — 본인 즉시 취소와 불발·중단 환불. 결제가 PAID 인 흐름이라 <b>주문 행 → 수량 → 결제 행</b> 차례(결정 37).
 *
 * <ul>
 *   <li><b>본인 즉시 취소</b>(줄·수량 단위): 마감 후 발주 공구는 JOINED 이고 공구가 모집 중·집계 중일 때(집계 중 취소로 불발이 될 수 있다 — 결정 45, 판정과는 수량 조건부 UPDATE 가
 *       하나만 이기게 한다), 즉시 발송 공구는 CONFIRMED(준비중 전)일 때. 발송 지연 공지가 있으면 그 전에 결제한 발송 전 주문은 승인 없이 <b>주문 전체</b>를 재부과 없이 취소(결정 52).
 *       그 밖은 409 {@code GROUP_BUY_CANCEL_NOT_ALLOWED}(취소 요청 → 운영자 승인 — 2b-3).</li>
 *   <li>금액: 주문 전체 취소면 남은 결제액 전부(결정 42·69). 일부 줄만 취소해 무료배송 기준 아래로 떨어지면 남은 상품 기준 배송비를 다시 계산해 그 차액을 빼고
 *       {@code reshipping_fee_charged} 에 누계(반품비 없음 — {@link GroupBuyPricing#reshippingFee}).</li>
 *   <li>주문 상태는 줄 전체로(결정 46): 남은 수량이 0 이면 CANCELED, 아니면 그대로(줄 수량만).</li>
 *   <li><b>불발·중단 환불</b>({@link #refundWhole}): 발송 전 주문(JOINED·CONFIRMED·PREPARING) → REFUNDED, 남은 줄 전부 CANCEL, 남은 결제액 전부. 주문 행을 잠그지 못하면 건너뛴다(SKIP LOCKED).</li>
 * </ul>
 */
@Service
public class ParticipationCancelService {

    private final ParticipationStore participations;
    private final GroupBuyStore groups;
    private final GroupBuyQtyService qty;
    private final GroupBuyRefundService refunds;
    private final GroupBuyRequestStore requests;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;
    private final TransactionTemplate separate;
    private final Clock clock;

    public ParticipationCancelService(ParticipationStore participations, GroupBuyStore groups, GroupBuyQtyService qty, GroupBuyRefundService refunds,
                                      GroupBuyRequestStore requests, SnowflakeIdGenerator ids, PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
        this.participations = participations;
        this.groups = groups;
        this.qty = qty;
        this.refunds = refunds;
        this.requests = requests;
        this.ids = ids;
        this.tx = new TransactionTemplate(transactions);
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 취소 결과 — 환불액, 재부과 배송비, 주문 전체가 취소됐는가 */
    public record CancelResult(long refundAmount, long reshippingFee, boolean wholeOrder, ParticipationStatus status) {
    }

    /**
     * 본인 즉시 취소 — {@code POST /api/me/orders/{orderNo}/cancel}(2b-4). {@code lines} 가 비면 주문 전체.
     *
     * @throws GroupBuyException   주문 없음(남의 주문 포함 404) · 즉시 취소가 안 되는 때(409 {@code CANCEL_NOT_ALLOWED})
     * @throws ValidationException 줄·수량이 주문에 없거나 가진 수량을 넘는다
     */
    public CancelResult cancelByMember(long userId, long participationId, List<OrderLine> lines) {
        return tx.execute(status -> {
            Instant now = now();
            ParticipationRow p = participations.lock(participationId).filter(row -> row.userId() == userId).orElseThrow(GroupBuyException::orderNotFound);
            GroupBuyRow group = groups.find(p.groupBuyId()).orElseThrow(GroupBuyException::notFound);
            List<ItemRow> items = participations.items(p.id());
            Optional<Instant> delayNotice = groups.firstDelayNoticeAt(group.id());
            boolean delayCancel = delayNotice.isPresent() && ParticipationStatus.PAID_BEFORE_SHIPPING.contains(p.status()) && p.joinedAt() != null
                    && !p.joinedAt().isAfter(delayNotice.get());
            boolean immediate = Fulfillment.IMMEDIATE.name().equals(group.fulfillment());
            boolean allowed = delayCancel || (immediate ? p.status() == ParticipationStatus.CONFIRMED : p.status() == ParticipationStatus.JOINED);
            if (!allowed) {
                throw GroupBuyException.cancelNotAllowed();
            }
            Map<Long, Integer> plan = delayCancel ? all(items) : plan(items, lines);
            boolean requireAccepting = !immediate && !delayCancel; // 성사형 JOINED — 판정과 하나만 이긴다(결정 45)
            return apply(p, group, items, plan, requireAccepting, delayCancel ? RefundCause.DELAY : RefundCause.USER_CANCEL, !delayCancel, null, now);
        });
    }

    /**
     * 불발·중단 환불 한 건(새 트랜잭션) — 발송 전 결제된 주문만. 주문 행이 잠겨 있으면 건너뛴다(다음 바퀴).
     *
     * @return 이번에 환불했으면 true
     */
    public boolean refundWhole(long participationId, RefundCause cause) {
        Boolean done = separate.execute(status -> {
            Instant now = now();
            Optional<ParticipationRow> locked = participations.lockSkipLocked(participationId);
            if (locked.isEmpty() || !ParticipationStatus.PAID_BEFORE_SHIPPING.contains(locked.get().status())) {
                return false;
            }
            ParticipationRow p = locked.get();
            GroupBuyRow group = groups.find(p.groupBuyId()).orElseThrow();
            List<ItemRow> items = participations.items(p.id());
            apply(p, group, items, all(items), false, cause, false, null, now);
            return true;
        });
        return Boolean.TRUE.equals(done);
    }

    /**
     * 결제된 주문의 줄·수량을 취소하고 돌려준다 — 취소 요청 승인·운영 환불(발송 전)·공급 불가·지연 취소가 쓰는 한 길(결정 32·42·52·53). 부르는 쪽 트랜잭션 안, 주문 행을 잠근 뒤.
     *
     * @param recalcShipping 일부 취소의 배송비 재계산(사업자 귀책이면 false — 결정 53)
     */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public CancelResult cancelLines(ParticipationRow locked, Map<Long, Integer> plan, RefundCause cause, boolean recalcShipping, GroupBuyRefundService.Extra extra,
                                    Long actorId, String reason) {
        GroupBuyRow group = groups.find(locked.groupBuyId()).orElseThrow(GroupBuyException::notFound);
        List<ItemRow> items = participations.items(locked.id());
        return apply(locked, group, items, plan, false, cause, recalcShipping, extra, actorId, reason, now());
    }

    /** 주문 줄 → 남은 수량 전부(주문 전체) */
    public static Map<Long, Integer> wholeOrder(List<ItemRow> items) {
        return all(items);
    }

    /**
     * 주문이 전부 환불·취소된 뒤 열린 요청을 닫는다(결정 69 — SUPERSEDED). 받아 둔 반품비 추가 결제는 모두 돌려준다(결정 68).
     */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void supersedeOpenRequests(ParticipationRow p, Long actorId, Instant now) {
        for (GroupBuyRequestStore.RequestRow r : requests.openOf(p.id())) {
            if (requests.transition(r.id(), RequestStatus.OPEN, RequestStatus.SUPERSEDED, actorId, now) && r.feeHeld() > 0 && r.feePaymentOrderId() != null) {
                refunds.refundReturnFee(p, r.id(), r.feePaymentOrderId(), r.feeHeld(), actorId, "SUPERSEDED", now);
            }
        }
    }

    /** 줄마다 취소할 수량 — 줄 ID → 수량(0 줄은 뺀다). 금액은 {@link GroupBuyRefundCalculator#cancel} 한 길 */
    private CancelResult apply(ParticipationRow p, GroupBuyRow group, List<ItemRow> items, Map<Long, Integer> plan, boolean requireAccepting, RefundCause cause,
                               boolean recalcShipping, Long actorId, Instant now) {
        return apply(p, group, items, plan, requireAccepting, cause, recalcShipping, GroupBuyRefundService.Extra.NONE, actorId, null, now);
    }

    private CancelResult apply(ParticipationRow p, GroupBuyRow group, List<ItemRow> items, Map<Long, Integer> plan, boolean requireAccepting, RefundCause cause,
                               boolean recalcShipping, GroupBuyRefundService.Extra extra, Long actorId, String reason, Instant now) {
        int remainingMain = 0;
        List<String> leftAddons = new ArrayList<>();
        Map<ItemRow, Integer> counts = new LinkedHashMap<>();
        List<GroupBuyRefundCalculator.Held> held = new ArrayList<>();
        List<GroupBuyRefundCalculator.Line> calcLines = new ArrayList<>();
        for (ItemRow item : GroupBuyQtyService.lockOrder(items)) {
            int count = plan.getOrDefault(item.id(), 0);
            if (count > item.heldQty()) {
                throw ValidationException.of("items", new MessageRef("groupbuy.cancel.lines.invalid"));
            }
            if (count > 0) {
                counts.put(item, count);
                calcLines.add(new GroupBuyRefundCalculator.Line(item.id(), count, null));
            }
            held.add(new GroupBuyRefundCalculator.Held(item.id(), item.skuId(), item.main(), item.unitPrice(), item.heldQty(), 0));
            int left = item.heldQty() - count;
            if (item.main()) {
                remainingMain += left;
            } else if (left > 0) {
                leftAddons.add(Long.toString(item.skuId()));
            }
        }
        if (counts.isEmpty()) {
            throw ValidationException.of("items", new MessageRef("groupbuy.cancel.lines.invalid"));
        }
        if (remainingMain == 0 && !leftAddons.isEmpty()) {
            // 본 상품 줄을 모두 취소하면 추가 구성상품만 남길 수 없다 — 함께 취소하라고 묻는다(결정 88)
            throw GroupBuyException.addonOnly(leftAddons);
        }
        for (Map.Entry<ItemRow, Integer> e : counts.entrySet()) {
            if (!participations.cancelItemQty(e.getKey().id(), e.getValue())) {
                throw GroupBuyException.cancelNotAllowed();
            }
        }
        if (!qty.cancel(group.id(), counts, requireAccepting, actorId, cause.name(), now)) {
            throw GroupBuyException.cancelNotAllowed(); // 그새 판정됐다 — 이제는 취소 요청으로(10-3 표)
        }
        GroupBuyRefundCalculator.Result calc = GroupBuyRefundCalculator.cancel(GroupBuyRefundCalculator.order(p), GroupBuyRefundCalculator.policy(group), held, calcLines,
                recalcShipping);
        long reshipping = calc.reshippingFee();
        if (reshipping > 0) {
            participations.addReshippingCharged(p.id(), reshipping, now);
        }
        ParticipationStatus to = p.status();
        if (calc.whole()) {
            to = cause == RefundCause.FAILED || cause == RefundCause.CANCELED ? ParticipationStatus.REFUNDED : ParticipationStatus.CANCELED;
            if (!participations.transition(p.id(), EnumSet.of(p.status()), to, now)) {
                throw GroupBuyException.cancelNotAllowed();
            }
            participations.insertHistory(ids.nextId(), p, p.status(), to, cause.name(), actorId, now);
        }
        StringJoiner lines = new StringJoiner(",", "[", "]");
        for (GroupBuyRefundCalculator.LineAmount l : calc.lines()) {
            long skuId = counts.keySet().stream().filter(i -> i.id() == l.itemId()).findFirst().orElseThrow().skuId();
            lines.add("{\"itemId\":\"" + l.itemId() + "\",\"skuId\":\"" + skuId + "\",\"qty\":" + l.qty() + ",\"amount\":" + l.amount() + "}");
        }
        GroupBuyRefundService.Parts parts = calc.parts();
        refunds.refund(p, cause, parts, extra, lines.toString(), actorId, reason, now);
        if (calc.whole()) {
            supersedeOpenRequests(p, actorId, now);
        }
        return new CancelResult(parts.amount(), reshipping, calc.whole(), to);
    }

    private static Map<Long, Integer> all(List<ItemRow> items) {
        Map<Long, Integer> out = new LinkedHashMap<>();
        items.forEach(item -> out.put(item.id(), item.heldQty()));
        return out;
    }

    /** 요청 줄(단품·수량) → 주문 줄 ID → 수량. 비면 전부 */
    private static Map<Long, Integer> plan(List<ItemRow> items, List<OrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return all(items);
        }
        Map<Long, Integer> out = new LinkedHashMap<>();
        List<Long> seen = new ArrayList<>();
        for (OrderLine line : lines) {
            ItemRow item = items.stream().filter(i -> i.skuId() == line.skuId()).findFirst().orElse(null);
            if (item == null || line.qty() < 1 || line.qty() > item.heldQty() || seen.contains(item.id())) {
                throw ValidationException.of("items", new MessageRef("groupbuy.cancel.lines.invalid"));
            }
            seen.add(item.id());
            out.put(item.id(), line.qty());
        }
        return out;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
