package com.community.withdrawal.service;

import com.community.withdrawal.dto.WithdrawalChoices;
import com.community.withdrawal.dto.WithdrawalPending;
import com.community.withdrawal.entity.WithdrawalPendingCode;
import com.community.withdrawal.repository.master.WithdrawalQueries;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 남은 일(끝나야 탈퇴가 완료되는 것 — commerce.md 17-1)을 센다. 신청 때(즉시 완료냐 예약이냐)와 자동 완료 배치(아직 남았나)가 <b>같은 판정</b>을 쓴다.
 *
 * <ul>
 *   <li>선택과 무관한 것: 진행 중 주문 · 열린 취소·반품 요청 · 끝나지 않은 결제 취소 · 송금 대기 환불 · 심사 중 포인트샵 환불.</li>
 *   <li>선택에 따른 것: 배송완료 7일(단순 변심 기한) 안 주문은 「기다리기」일 때만, 진행 중 광고·핫딜은 「기다리기」일 때만, 충전 포인트 환불은 「환불받기」이고 유상 잔액이 있을 때만
 *       (충전이 없어 지금은 생기지 않는다).</li>
 * </ul>
 */
@Component
@Transactional(readOnly = true)
public class WithdrawalPendingResolver {

    /** 단순 변심 반품 기한(배송완료 뒤 일수 — {@code GroupBuyRequestService.CHANGE_OF_MIND_DAYS} 와 같다) */
    public static final int RETURN_WINDOW_DAYS = 7;

    private static final String ORDER_PATH = "/me/orders/";

    private final WithdrawalQueries queries;
    private final WithdrawalBusinessInfo business;

    public WithdrawalPendingResolver(WithdrawalQueries queries, WithdrawalBusinessInfo business) {
        this.queries = queries;
        this.business = business;
    }

    /** 선택과 무관하게 이미 남아 있는 일(화면의 {@code pending}) */
    public List<WithdrawalPending> fixed(long userId) {
        List<WithdrawalPending> out = new ArrayList<>();
        for (WithdrawalQueries.Order o : queries.ordersInProgress(userId)) {
            out.add(new WithdrawalPending(WithdrawalPendingCode.GROUP_BUY_ORDER, o.orderNo(), ORDER_PATH + o.orderNo(), null));
        }
        for (WithdrawalQueries.Order r : queries.openRequests(userId)) {
            out.add(new WithdrawalPending(WithdrawalPendingCode.RETURN_REQUEST, r.orderNo(), ORDER_PATH + r.orderNo(), null));
        }
        for (WithdrawalQueries.Refund c : queries.openPaymentCancels(userId)) {
            out.add(new WithdrawalPending(WithdrawalPendingCode.PAYMENT_CANCEL, c.orderNo() != null ? c.orderNo() : c.label(), c.orderNo() == null ? null : ORDER_PATH + c.orderNo(), null));
        }
        for (WithdrawalQueries.Refund t : queries.openBankTransfers(userId)) {
            out.add(new WithdrawalPending(WithdrawalPendingCode.PAYMENT_CANCEL, t.orderNo() != null ? t.orderNo() : "#" + t.id(), t.orderNo() == null ? null : ORDER_PATH + t.orderNo(),
                    null));
        }
        for (WithdrawalQueries.ShopRefund s : queries.pendingShopRefunds(userId)) {
            out.add(new WithdrawalPending(WithdrawalPendingCode.POINT_SHOP_REFUND, s.itemName(), "/me/items", null));
        }
        return out;
    }

    /** 고른 것까지 합한 남은 일 전부 — 비어 있으면 바로 완료할 수 있다 */
    public List<WithdrawalPending> all(long userId, WithdrawalChoices choices, long paidPoints, Instant now) {
        List<WithdrawalPending> out = new ArrayList<>(fixed(userId));
        if (choices.waitDelivered()) {
            for (WithdrawalQueries.Order o : queries.deliveredWithin(userId, RETURN_WINDOW_DAYS, now)) {
                out.add(new WithdrawalPending(WithdrawalPendingCode.DELIVERED_RETURN_WINDOW, o.orderNo(), ORDER_PATH + o.orderNo(), o.deliveredAt().plus(Duration.ofDays(RETURN_WINDOW_DAYS))));
            }
        }
        if (!choices.forfeitBiz()) {
            for (WithdrawalBusinessInfo.Running r : business.of(userId, now).running()) {
                out.add(new WithdrawalPending(WithdrawalPendingCode.BIZ_APPLICATION, r.title(), "/business/applications/" + r.id(), r.endsAt()));
            }
        }
        if (!choices.forfeitPaidPoints() && paidPoints > 0) {
            // 충전(커머스 3단계)·충전 환불이 아직 없어 유상 잔액은 늘 0 — 이 줄은 3단계가 충전 환불 신청을 연결할 자리다(지금은 생기지 않는다)
            out.add(new WithdrawalPending(WithdrawalPendingCode.POINT_CHARGE_REFUND, Long.toString(paidPoints), "/me/points", null));
        }
        return out;
    }

    /** 남은 일의 {@code waitUntil} 중 가장 늦은 것(하나라도 모르면 비어 있다) */
    public static Optional<Instant> expectedCompleteAt(List<WithdrawalPending> pending) {
        if (pending.isEmpty() || pending.stream().anyMatch(p -> p.waitUntil() == null)) {
            return Optional.empty();
        }
        return pending.stream().map(WithdrawalPending::waitUntil).max(Instant::compareTo);
    }
}
