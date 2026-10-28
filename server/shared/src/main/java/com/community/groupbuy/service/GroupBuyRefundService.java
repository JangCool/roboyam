package com.community.groupbuy.service;

import com.community.groupbuy.entity.RefundCause;
import com.community.groupbuy.repository.master.GroupBuyRefundStore;
import com.community.groupbuy.repository.master.ParticipationStore;
import com.community.groupbuy.repository.master.ParticipationStore.ParticipationRow;
import com.community.payment.dto.PaymentCancelOrder;
import com.community.payment.service.PaymentCancelService;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 돈 돌려주기 한 건(결정 36·38) — 부르는 쪽 트랜잭션 안에서, 주문 행을 잠근 뒤에(결제가 PAID 인 흐름 — 주문 행 → 수량 → 결제 행 차례, 결정 37):
 * 환불 누계를 조건부로 올리고(보상까지 합쳐 남은 결제액을 넘지 않을 때만 — 공구가 상한의 정본) → 환불 기록 → 결제 취소 대기열({@code PaymentCancelService.request}, 요청 키 {@code GB_REFUND:<id>}).
 * 0원이면 취소를 넣지 않고 기록만 완료(method NONE).
 */
@Service
public class GroupBuyRefundService {

    /** 금액 나눔(결정 75) — 상품·배송비 환불, 재부과 배송비(−), 재부과 돌려줌(+) */
    public record Parts(long itemRefund, long shippingRefund, long reshippingFee, long reshippingRefund) {

        public long amount() {
            return itemRefund + shippingRefund - reshippingFee + reshippingRefund;
        }
    }

    private final ParticipationStore participations;
    private final GroupBuyRefundStore refunds;
    private final PaymentCancelService cancels;
    private final SnowflakeIdGenerator ids;

    public GroupBuyRefundService(ParticipationStore participations, GroupBuyRefundStore refunds, PaymentCancelService cancels, SnowflakeIdGenerator ids) {
        this.participations = participations;
        this.refunds = refunds;
        this.cancels = cancels;
        this.ids = ids;
    }

    /**
     * 환불을 넣는다.
     *
     * @return 환불 기록 ID
     * @throws IllegalStateException 남은 결제액을 넘는다(부르는 쪽의 결함 — 트랜잭션째 되돌린다)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long refund(ParticipationRow p, RefundCause cause, Parts parts, String linesJson, Long actorId, String reason, Instant now) {
        long amount = parts.amount();
        if (amount < 0) {
            throw new IllegalStateException("환불액이 음수다");
        }
        if (amount > 0 && !participations.addRefunded(p.id(), amount, now)) {
            throw new IllegalStateException("환불액이 남은 결제액을 넘는다 participationId=" + p.id());
        }
        long refundId = ids.nextId();
        refunds.insert(new GroupBuyRefundStore.NewRefund(refundId, p.id(), p.groupBuyId(), p.userId(), cause, parts.itemRefund(), parts.shippingRefund(),
                parts.reshippingFee(), parts.reshippingRefund(), amount, p.paymentOrderId(), linesJson, actorId, reason, now));
        if (amount > 0) {
            long cancelId = cancels.request(new PaymentCancelOrder(p.paymentOrderId(), amount, cause.cancelKind(), "공구 " + cause.name() + " " + p.orderNo(),
                    GroupBuyRefundStore.requestKey(refundId), actorId));
            refunds.attachCancel(refundId, cancelId);
        }
        return refundId;
    }
}
