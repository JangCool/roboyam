package com.community.groupbuy.service;

import com.community.groupbuy.entity.RefundCause;
import com.community.groupbuy.repository.master.GroupBuyRefundStore;
import com.community.groupbuy.repository.master.ParticipationStore;
import com.community.groupbuy.repository.master.ParticipationStore.ParticipationRow;
import com.community.payment.dto.CancelKind;
import com.community.payment.dto.PaymentCancelOrder;
import com.community.payment.service.PaymentCancelService;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 돈 돌려주기 한 건(결정 36·38) — 부르는 쪽 트랜잭션 안에서, 주문 행을 잠근 뒤에(결제가 PAID 인 흐름 — 주문 행 → 수량 → 결제 행 차례, 결정 37):
 * 환불·보상 누계를 조건부로 올리고(합쳐 남은 결제액을 넘지 않을 때만 — 공구가 상한의 정본) → 환불 기록 → 결제 취소 대기열({@code PaymentCancelService.request}, 요청 키 {@code GB_REFUND:<id>}).
 * 0원이면 취소를 넣지 않고 기록만 완료(method NONE). 반품비 추가 결제의 남는 몫은 {@link #refundReturnFee} — 그 결제에서 취소(결정 68).
 */
@Service
public class GroupBuyRefundService {

    /**
     * 금액 나눔(결정 75) — 상품·배송비 환불, 재부과 배송비(−), 재부과 돌려줌(+), 반품비(−), PG 로 돌려주는 고객 택배비 보상(+), 반품비 추가 결제로 메운 몫(+ — 결정 68).
     */
    public record Parts(long itemRefund, long shippingRefund, long reshippingFee, long reshippingRefund, long returnFee, long compensationPg, long feeCovered) {

        public Parts(long itemRefund, long shippingRefund, long reshippingFee, long reshippingRefund) {
            this(itemRefund, shippingRefund, reshippingFee, reshippingRefund, 0, 0, 0);
        }

        public long amount() {
            return itemRefund + shippingRefund - reshippingFee + reshippingRefund - returnFee + compensationPg + feeCovered;
        }

        public Parts withCompensation(long pg) {
            return new Parts(itemRefund, shippingRefund, reshippingFee, reshippingRefund, returnFee, pg, feeCovered);
        }

        public Parts withFeeCovered(long covered) {
            return new Parts(itemRefund, shippingRefund, reshippingFee, reshippingRefund, returnFee, compensationPg, covered);
        }
    }

    /**
     * 환불 기록의 덧붙임 — 요청 ID, 계좌로 보낼 보상(PG 밖), 운영자가 금액을 정한 몫(고액 문턱 누계 — 결정 70).
     */
    public record Extra(Long requestId, long compensationBank, long operatorAmount) {

        public static final Extra NONE = new Extra(null, 0, 0);
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

    /** 요청 없는 환불(불발·중단·본인 취소) */
    @Transactional(propagation = Propagation.MANDATORY)
    public long refund(ParticipationRow p, RefundCause cause, Parts parts, String linesJson, Long actorId, String reason, Instant now) {
        return refund(p, cause, parts, Extra.NONE, linesJson, actorId, reason, now);
    }

    /**
     * 환불을 넣는다.
     *
     * @return 환불 기록 ID
     * @throws IllegalStateException 남은 결제액을 넘는다(부르는 쪽의 결함 — 트랜잭션째 되돌린다)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long refund(ParticipationRow p, RefundCause cause, Parts parts, Extra extra, String linesJson, Long actorId, String reason, Instant now) {
        long amount = parts.amount();
        if (amount < 0 || parts.compensationPg() < 0) {
            throw new IllegalStateException("환불액이 음수다");
        }
        if (amount > 0 && !participations.addRefunded(p.id(), amount - parts.compensationPg(), parts.compensationPg(), now)) {
            throw new IllegalStateException("환불액이 남은 결제액을 넘는다 participationId=" + p.id());
        }
        long refundId = ids.nextId();
        refunds.insert(new GroupBuyRefundStore.NewRefund(refundId, p.id(), p.groupBuyId(), p.userId(), cause, extra.requestId(), parts, extra.compensationBank(), 0,
                extra.operatorAmount(), amount, p.paymentOrderId(), linesJson, actorId, reason, now));
        if (amount > 0) {
            long cancelId = cancels.request(new PaymentCancelOrder(p.paymentOrderId(), amount, cause.cancelKind(), "공구 " + cause.name() + " " + p.orderNo(),
                    GroupBuyRefundStore.requestKey(refundId), actorId));
            refunds.attachCancel(refundId, cancelId);
        }
        return refundId;
    }

    /**
     * 반품비 추가 결제의 남는 몫을 그 결제에서 부분 취소한다(결정 68 — 요청 철회·거절·완료 때 다시 계산한 부족분이 낸 돈보다 작을 때). 요청의 환불 누계를 조건부로 올린다(낸 돈을 넘지 않게).
     *
     * @return 환불 기록 ID(0원이면 넣지 않고 null)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long refundReturnFee(ParticipationRow p, long requestId, long feePaymentOrderId, long amount, Long actorId, String reason, Instant now) {
        if (amount <= 0) {
            return null;
        }
        if (!refunds.addFeeRefunded(requestId, amount, now)) {
            throw new IllegalStateException("반품비 환불이 낸 금액을 넘는다 requestId=" + requestId);
        }
        long refundId = ids.nextId();
        refunds.insert(new GroupBuyRefundStore.NewRefund(refundId, p.id(), p.groupBuyId(), p.userId(), RefundCause.RETURN_FEE_REFUND, requestId,
                new Parts(0, 0, 0, 0), 0, amount, 0, amount, feePaymentOrderId, null, actorId, reason, now));
        long cancelId = cancels.request(new PaymentCancelOrder(feePaymentOrderId, amount, CancelKind.REFUND, "공구 반품비 환불 " + p.orderNo(),
                GroupBuyRefundStore.requestKey(refundId), actorId));
        refunds.attachCancel(refundId, cancelId);
        return refundId;
    }
}
