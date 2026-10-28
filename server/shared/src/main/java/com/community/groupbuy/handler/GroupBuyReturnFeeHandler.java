package com.community.groupbuy.handler;

import com.community.groupbuy.dto.GroupBuyEvents;
import com.community.groupbuy.repository.master.GroupBuyRefundStore;
import com.community.groupbuy.repository.master.GroupBuyRequestStore;
import com.community.payment.dto.CanceledPayment;
import com.community.payment.dto.ManualCancelResolution;
import com.community.payment.dto.PaidPayment;
import com.community.payment.dto.PaymentDecision;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.handler.PaymentOrderHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 반품비 부족분 추가 결제 처리기({@code GROUP_BUY_RETURN_FEE} — 계약 결정 68). 대상 = 반품 요청, 선점 없음.
 *
 * <ul>
 *   <li>{@code onPaid}: 요청이 부족분 결제 전(AWAITING_FEE)이고 닫히기 전이고 금액이 같으면 접수(REQUESTED) — 아니면 거절(자동 취소). 도메인 사정으로 예외를 던지지 않는다(결정 62 와 같다).</li>
 *   <li>{@code onCanceled}: 남는 몫 환불(원인 RETURN_FEE_REFUND)의 기록을 완료로 — 표시·알림만.</li>
 * </ul>
 */
@Component
public class GroupBuyReturnFeeHandler implements PaymentOrderHandler {

    public static final String REJECT_CLOSED = "GROUP_BUY_RETURN_FEE_CLOSED";

    private final GroupBuyRequestStore requests;
    private final GroupBuyRefundStore refunds;
    private final GroupBuyManualRefunds manual;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public GroupBuyReturnFeeHandler(GroupBuyRequestStore requests, GroupBuyRefundStore refunds, GroupBuyManualRefunds manual, ApplicationEventPublisher events,
                                    ObjectProvider<Clock> clocks) {
        this.requests = requests;
        this.refunds = refunds;
        this.manual = manual;
        this.events = events;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Override
    public PaymentOrderType orderType() {
        return PaymentOrderType.GROUP_BUY_RETURN_FEE;
    }

    @Override
    public PaymentDecision onPaid(PaidPayment payment) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (!requests.acceptFee(payment.orderRefId(), payment.paymentOrderId(), payment.amount(), now)) {
            return PaymentDecision.reject(REJECT_CLOSED);
        }
        requests.find(payment.orderRefId()).ifPresent(r -> events.publishEvent(new GroupBuyEvents.RequestChanged(r.id(), r.participationId(), r.userId(), "REQUESTED",
                "RETURN_FEE_PAID")));
        return PaymentDecision.accept();
    }

    @Override
    public void onCanceled(CanceledPayment cancellation) {
        if (cancellation.anomaly() != null) {
            return; // 늦게 온 부족분 결제의 자동 취소 — 요청은 그대로(이미 닫혔다)
        }
        refunds.completeByCancel(cancellation.cancelId(), clock.instant().truncatedTo(ChronoUnit.MICROS))
                .ifPresent(refund -> events.publishEvent(new GroupBuyEvents.RefundCompleted(refund.id(), refund.participationId(), refund.userId())));
    }

    @Override
    public void onCancelResolvedManually(ManualCancelResolution resolution) {
        manual.resolve(resolution);
    }
}
