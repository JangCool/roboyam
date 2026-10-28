package com.community.groupbuy.service;

import com.community.groupbuy.entity.CollectionMethod;
import com.community.groupbuy.entity.RequestReason;
import com.community.groupbuy.entity.ReturnFeeMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 공구 환불 금액 계산 <b>한 곳</b>(commerce.md 10-8 기준 표, 계약 결정 33·42·43·44·68·69·71) — 요청 미리 보기·승인·완료·본인 취소·운영 환불이 같은 값을 쓴다. 순수 계산(저장소를 모른다).
 *
 * <h3>발송 전 취소({@link #cancel})</h3>
 * 주문 전체면 남은 결제액 전부(앞서 재부과한 배송비도 돌려준다 — 결정 69). 일부 줄이면 그 줄 금액에서, 무료배송 기준 아래로 떨어질 때 남은 상품 기준 배송비 차액을 뺀다(결정 42 — 반품비 없음,
 * 사업자 귀책(공급 불가·지연)이면 다시 계산하지 않는다).
 *
 * <h3>반품({@link #returnRefund})</h3>
 * <ul>
 *   <li>부담: 요청에 하자·오배송·훼손 줄이 하나라도 있으면 업체(반품비 없음 — 결정 44), 아니면 회원.</li>
 *   <li>줄 금액 = 단가 × 수량 − 그 줄에 앞서 금액만 돌려준 운영 환불의 남은 몫(결정 71).</li>
 *   <li>「낸 주문 / 무료배송 주문」은 발송 때 고정한 처음 배송비({@code shipped_shipping_fee} — 발송 전 일부 취소로 재부과한 배송비 포함, 결정 43)로 가른다.</li>
 *   <li>회원 부담: 업체회수면 반품비를 뺀다. 낸 주문은 처음 배송비를 돌려주지 않는다. 무료배송 주문의 일부 반품은 남은 상품 기준으로 배송비를 다시 계산해 이미 재부과한 것과의 차액만 뺀다(결정 33).
 *       마지막 남은 것까지 반품(주문 전체)이면 처음 배송비 처리만 한 번에 전체 반품한 것과 같게 — 편도(또는 고객발송)는 발송 뒤 재부과분을 돌려주고, 왕복 + 업체회수는 처음 배송비를 한 번만 받은 것으로(결정 43).</li>
 *   <li>업체 부담: 반품비·재계산 없음. 주문 전체면 처음 배송비(발송 전 재부과분 포함)와 발송 뒤 재부과분까지 모두 돌려준다.</li>
 *   <li>결과가 음수일 수 있다(반품비·재부과가 상품값보다 큰 단순 변심 — 결정 68: 회원이 부족분을 추가 결제하거나 고객발송을 고른다). 양수면 남은 결제액을 넘지 않는다.</li>
 * </ul>
 */
public final class GroupBuyRefundCalculator {

    private GroupBuyRefundCalculator() {
    }

    /**
     * 주문 쪽 값.
     *
     * @param shippingFee          주문 때 낸 배송비
     * @param shippedShippingFee   발송 때 고정한 처음 배송비(아직 발송 전이면 null — 낸 배송비 + 재부과로 본다)
     * @param reshippingFeeCharged 지금까지 재부과한 배송비(발송 전·뒤 모두)
     * @param remainingCash        남은 결제액(현금 − 환불 누계 − 보상 누계)
     */
    public record OrderView(long shippingFee, Long shippedShippingFee, long reshippingFeeCharged, long remainingCash) {

        long firstShipping() {
            return shippedShippingFee != null ? shippedShippingFee : shippingFee + reshippingFeeCharged;
        }

        /** 발송 뒤 반품으로 재부과한 몫(결정 43 의 환급 대상) */
        long postShipReshipping() {
            return Math.max(0, reshippingFeeCharged - (firstShipping() - shippingFee));
        }
    }

    /** 공구가 모집 시작 때 고정한 배송비 사본(10-2) */
    public record PolicyView(long baseFee, Long freeShippingOver, long returnFee, ReturnFeeMode mode) {
    }

    /**
     * 주문 줄의 지금 값.
     *
     * @param heldQty         아직 가진 수량(qty − 취소 − 반품)
     * @param priorRefundLeft 그 줄에 금액만 돌려준 운영 환불 중 아직 반품 환불에서 빼지 않은 몫(결정 71)
     */
    public record Held(long itemId, long skuId, boolean main, long unitPrice, int heldQty, long priorRefundLeft) {
    }

    /** 요청 줄 — 주문 줄·수량·사유(발송 전 취소는 사유 없음) */
    public record Line(long itemId, int qty, RequestReason reason) {
    }

    /** 줄별 금액과 그 줄에서 뺀 운영 환불 몫 */
    public record LineAmount(long itemId, int qty, long amount, long priorOffset) {
    }

    /**
     * 계산 결과(결정 75 의 금액 나눔).
     *
     * @param total           상품 + 배송비 환불 − 반품비 − 재부과 + 재부과 돌려줌(음수 가능 — 반품만)
     * @param reshippingDelta 주문의 재부과 누계에 더할 값(재부과 − 돌려줌, 음수 가능)
     * @param whole           이 요청으로 주문의 남은 줄이 모두 빠지는가
     */
    public record Result(List<LineAmount> lines, long itemRefund, long shippingRefund, long returnFee, long reshippingFee, long reshippingRefund, boolean sellerFault,
                         boolean whole, long total, long reshippingDelta) {

        public GroupBuyRefundService.Parts parts() {
            return new GroupBuyRefundService.Parts(itemRefund, shippingRefund, reshippingFee, reshippingRefund, returnFee, 0, 0);
        }
    }

    /** 주문 행 → 계산기 값 */
    public static OrderView order(com.community.groupbuy.repository.master.ParticipationStore.ParticipationRow p) {
        return new OrderView(p.shippingFee(), p.shippedShippingFee(), p.reshippingFeeCharged(), p.remainingCash());
    }

    /** 공구 행(모집 시작 때 고정한 배송비 사본) → 계산기 값 */
    public static PolicyView policy(com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow g) {
        return new PolicyView(g.shippingFee() == null ? 0 : g.shippingFee(), g.freeShippingOver(), g.returnShippingFee() == null ? 0 : g.returnShippingFee(),
                g.returnFeeMode() == null ? ReturnFeeMode.ONE_WAY : ReturnFeeMode.valueOf(g.returnFeeMode()));
    }

    /**
     * 발송 전 취소(본인 즉시 취소·취소 요청 승인·운영 환불·공급 불가·지연 취소·불발·중단 — 결정 42·52·53·69).
     *
     * @param recalcShipping 일부 취소에 배송비를 다시 계산하는가(사업자 귀책이면 false)
     */
    public static Result cancel(OrderView order, PolicyView policy, List<Held> held, List<Line> lines, boolean recalcShipping) {
        Map<Long, Integer> plan = plan(lines);
        long canceled = 0;
        long remainingItems = 0;
        List<LineAmount> amounts = new ArrayList<>();
        for (Held h : held) {
            int count = plan.getOrDefault(h.itemId(), 0);
            canceled += h.unitPrice() * count;
            remainingItems += h.unitPrice() * (h.heldQty() - count);
            if (count > 0) {
                amounts.add(new LineAmount(h.itemId(), count, h.unitPrice() * count, 0));
            }
        }
        boolean whole = remainingItems == 0 && held.stream().allMatch(h -> h.heldQty() == plan.getOrDefault(h.itemId(), 0));
        long cash = order.remainingCash();
        if (whole) {
            long item = Math.min(canceled, cash);
            long rest = cash - item;
            long back = Math.min(rest, order.reshippingFeeCharged());
            return new Result(amounts, item, rest - back, 0, 0, back, false, true, cash, -back);
        }
        long reshipping = recalcShipping ? GroupBuyPricing.reshippingFee(remainingItems, policy.baseFee(), policy.freeShippingOver(), order.shippingFee(),
                order.reshippingFeeCharged()) : 0;
        long item = Math.min(canceled, cash);
        reshipping = Math.min(reshipping, item);
        return new Result(spread(amounts, -reshipping), item, 0, 0, reshipping, 0, false, false, item - reshipping, reshipping);
    }

    /** 반품(배송완료 뒤 — commerce.md 10-8 표, 결정 33·43·44·68·71) */
    public static Result returnRefund(OrderView order, PolicyView policy, List<Held> held, List<Line> lines, CollectionMethod method) {
        Map<Long, Integer> plan = plan(lines);
        boolean sellerFault = lines.stream().anyMatch(l -> l.reason() != null && l.reason().sellerFault());
        long itemRefund = 0;
        long remainingItems = 0;
        boolean whole = true;
        List<LineAmount> amounts = new ArrayList<>();
        Map<Long, RequestReason> reasons = new LinkedHashMap<>();
        lines.forEach(l -> reasons.put(l.itemId(), l.reason()));
        for (Held h : held) {
            int count = plan.getOrDefault(h.itemId(), 0);
            int left = h.heldQty() - count;
            remainingItems += h.unitPrice() * left;
            if (left > 0) {
                whole = false;
            }
            if (count > 0) {
                long gross = h.unitPrice() * count;
                long offset = Math.min(h.priorRefundLeft(), gross);
                itemRefund += gross - offset;
                amounts.add(new LineAmount(h.itemId(), count, gross - offset, offset));
            }
        }
        long firstShipping = order.firstShipping();
        long postShip = order.postShipReshipping();
        long returnFee = 0;
        long reshippingFee = 0;
        long reshippingRefund = 0;
        long shippingRefund = 0;
        if (sellerFault) {
            if (whole) {
                // 업체 부담 전체 반품 — 처음 배송비(발송 전 재부과 포함)와 발송 뒤 재부과까지 모두 돌려준다
                shippingRefund = order.shippingFee();
                reshippingRefund = order.reshippingFeeCharged();
            }
        } else {
            if (method == CollectionMethod.COMPANY_PICKUP) {
                returnFee = policy.returnFee();
            }
            boolean freeOrder = firstShipping == 0;
            if (freeOrder) {
                if (whole) {
                    if (policy.mode() == ReturnFeeMode.ROUND_TRIP && method == CollectionMethod.COMPANY_PICKUP) {
                        long adjust = policy.baseFee() - postShip; // 처음 배송비를 한 번만 받은 것으로
                        reshippingFee = Math.max(0, adjust);
                        reshippingRefund = Math.max(0, -adjust);
                    } else {
                        reshippingRefund = postShip; // 편도·고객발송 — 처음 배송비 0 이 되게 재부과를 돌려준다
                    }
                } else {
                    reshippingFee = GroupBuyPricing.reshippingFee(remainingItems, policy.baseFee(), policy.freeShippingOver(), 0, postShip);
                }
            }
            // 낸 주문의 단순 변심 — 처음 배송비는 돌려주지 않는다(전체·일부, 편도·왕복 같음)
        }
        long total = itemRefund + shippingRefund - returnFee - reshippingFee + reshippingRefund;
        if (total > order.remainingCash()) {
            total = order.remainingCash();
            itemRefund = total - shippingRefund + returnFee + reshippingFee - reshippingRefund;
        }
        long extra = shippingRefund - returnFee - reshippingFee + reshippingRefund;
        return new Result(spread(amounts, extra, reasons), itemRefund, shippingRefund, returnFee, reshippingFee, reshippingRefund, sellerFault, whole, total,
                reshippingFee - reshippingRefund);
    }

    /** 줄 → 수량 */
    private static Map<Long, Integer> plan(List<Line> lines) {
        Map<Long, Integer> out = new LinkedHashMap<>();
        lines.forEach(l -> out.merge(l.itemId(), l.qty(), Integer::sum));
        return out;
    }

    private static List<LineAmount> spread(List<LineAmount> amounts, long extra) {
        return spread(amounts, extra, Map.of());
    }

    /** 배송비·반품비 몫은 회원 부담 줄(단순 변심·기타)의 마지막 줄에 — 없으면 마지막 줄에(하자 줄은 전액 — 결정 44) */
    private static List<LineAmount> spread(List<LineAmount> amounts, long extra, Map<Long, RequestReason> reasons) {
        if (amounts.isEmpty() || extra == 0) {
            return List.copyOf(amounts);
        }
        int target = amounts.size() - 1;
        for (int i = amounts.size() - 1; i >= 0; i--) {
            RequestReason reason = reasons.get(amounts.get(i).itemId());
            if (reason == null || !reason.sellerFault()) {
                target = i;
                break;
            }
        }
        List<LineAmount> out = new ArrayList<>(amounts);
        LineAmount t = out.get(target);
        out.set(target, new LineAmount(t.itemId(), t.qty(), t.amount() + extra, t.priorOffset()));
        return List.copyOf(out);
    }
}
