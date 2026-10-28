package com.community.groupbuy.service;

import com.community.groupbuy.dto.OrderLine;
import com.community.groupbuy.entity.Fulfillment;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.RefundCause;
import com.community.groupbuy.exception.GroupBuyException;
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
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;
    private final TransactionTemplate separate;
    private final Clock clock;

    public ParticipationCancelService(ParticipationStore participations, GroupBuyStore groups, GroupBuyQtyService qty, GroupBuyRefundService refunds,
                                      SnowflakeIdGenerator ids, PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
        this.participations = participations;
        this.groups = groups;
        this.qty = qty;
        this.refunds = refunds;
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

    /** 줄마다 취소할 수량 — 줄 ID → 수량(0 줄은 뺀다) */
    private CancelResult apply(ParticipationRow p, GroupBuyRow group, List<ItemRow> items, Map<Long, Integer> plan, boolean requireAccepting, RefundCause cause,
                               boolean recalcShipping, Long actorId, Instant now) {
        long canceledAmount = 0;
        long remainingItems = 0;
        int remainingQty = 0;
        int remainingMain = 0;
        List<String> leftAddons = new ArrayList<>();
        Map<ItemRow, Integer> counts = new LinkedHashMap<>();
        StringJoiner lines = new StringJoiner(",", "[", "]");
        for (ItemRow item : GroupBuyQtyService.lockOrder(items)) {
            int count = plan.getOrDefault(item.id(), 0);
            if (count > 0) {
                counts.put(item, count);
                long amount = item.unitPrice() * count;
                canceledAmount += amount;
                lines.add("{\"itemId\":\"" + item.id() + "\",\"skuId\":\"" + item.skuId() + "\",\"qty\":" + count + ",\"amount\":" + amount + "}");
            }
            int left = item.heldQty() - count;
            remainingQty += left;
            remainingItems += item.unitPrice() * left;
            if (item.main()) {
                remainingMain += left;
            } else if (left > 0) {
                leftAddons.add(Long.toString(item.skuId()));
            }
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
        if (!counts.isEmpty() && !qty.cancel(group.id(), counts, requireAccepting, actorId, cause.name(), now)) {
            throw GroupBuyException.cancelNotAllowed(); // 그새 판정됐다 — 이제는 취소 요청으로(10-3 표)
        }
        boolean whole = remainingQty == 0;
        long remainingCash = p.remainingCash();
        GroupBuyRefundService.Parts parts;
        long reshipping = 0;
        if (whole) {
            // 전액 = 남은 결제액(결정 69) — 상품 + 나머지(처음 배송비·앞서 재부과한 배송비 돌려줌)
            long item = Math.min(canceledAmount, remainingCash);
            long rest = remainingCash - item;
            long reshippingBack = Math.min(rest, p.reshippingFeeCharged());
            parts = new GroupBuyRefundService.Parts(item, rest - reshippingBack, 0, reshippingBack);
        } else {
            if (recalcShipping) {
                reshipping = GroupBuyPricing.reshippingFee(remainingItems, Objects.requireNonNullElse(group.shippingFee(), 0L), group.freeShippingOver(), p.shippingFee(),
                        p.reshippingFeeCharged());
            }
            long item = Math.min(canceledAmount, remainingCash);
            reshipping = Math.min(reshipping, item);
            parts = new GroupBuyRefundService.Parts(item, 0, reshipping, 0);
        }
        if (reshipping > 0) {
            participations.addReshippingCharged(p.id(), reshipping, now);
        }
        ParticipationStatus to = p.status();
        if (whole) {
            to = cause == RefundCause.FAILED || cause == RefundCause.CANCELED ? ParticipationStatus.REFUNDED : ParticipationStatus.CANCELED;
            if (!participations.transition(p.id(), EnumSet.of(p.status()), to, now)) {
                throw GroupBuyException.cancelNotAllowed();
            }
            participations.insertHistory(ids.nextId(), p, p.status(), to, cause.name(), actorId, now);
        }
        refunds.refund(p, cause, parts, lines.toString(), actorId, null, now);
        return new CancelResult(parts.amount(), reshipping, whole, to);
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
