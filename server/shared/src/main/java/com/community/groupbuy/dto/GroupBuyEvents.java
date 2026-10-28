package com.community.groupbuy.dto;

import com.community.groupbuy.entity.QtyMovementKind;
import java.time.Instant;
import java.util.List;

/**
 * 공구가 내는 업무 이벤트 — 회원 알림·메일·운영자 알림은 2b-4 가 이 이벤트를 커밋 뒤에 받아 보낸다(지금은 받는 곳이 없다). 업무 트랜잭션 안에서 발행한다.
 */
public final class GroupBuyEvents {

    private GroupBuyEvents() {
    }

    /** 남은 수량이 늘었다(선점 만료·본인 취소·다시 판매·추가 입고 — 수량 풀림 알림, 결정 81: 공구·단품 둘 다 남을 때만 보낸다) */
    public record QtyReleased(long groupBuyId, Long skuId, QtyMovementKind kind) {
    }

    /** 결제가 확정됐다(주문 확인 = 계약 내용 통지 메일) */
    public record OrderPaid(long participationId, long groupBuyId, long userId) {
    }

    /** 결제를 받지 않았다(도메인 거절 — 자동 취소 안내, 운영자 결제 이상) */
    public record OrderRejected(long participationId, long groupBuyId, long userId, String reason, boolean anomaly) {
    }

    /** 선점이 만료됐다(본인 취소가 아니면 알림 — 10-6 4) */
    public record HoldExpired(long participationId, long groupBuyId, long userId) {
    }

    /** 추가 차수가 모집을 시작했다 — 루트의 모든 차수의 NEXT_ROUND·RESTOCK·CLOSING_SOON 신청자(같은 회원 한 번, 결정 31) */
    public record RoundStarted(long roundRootId, long groupBuyId, int roundNo, List<Long> recipients) {
    }

    /** 환불이 끝났다(PG 취소 성공 — 환불 완료 알림·메일) */
    public record RefundCompleted(long refundId, long participationId, long userId) {
    }

    /** 자동 취소(늦은 결제·도메인 거절)가 끝났다 — 주문 금액·상태는 그대로, 회원 안내만(결정 40) */
    public record AutoCancelCompleted(long participationId, long userId, long paymentOrderId) {
    }

    /** 에스크로 호출을 재시도 끝에 그만뒀다(운영자 알림 — 결정 64) */
    public record EscrowGaveUp(long shipmentId, long participationId, String kind) {
    }

    /** 요청 상태가 바뀌었다(접수·승인·회수 확인·완료·거절·교환 → 환불 전환 등 — 회원 알림, 운영자 요청 알림 COMMERCE_REFUND_REQUESTED) */
    public record RequestChanged(long requestId, long participationId, long userId, String status, String reason) {
    }

    /** 단품 공급 불가로 회원에게 「나머지만 받기 / 전체 취소」를 묻는다(결정 53 — 앱 + 메일) */
    public record SupplyChoiceAsked(long participationId, long userId, long skuId, Instant deadline) {
    }

    /** 운영자가 콘솔에서 직접 처리한 환불(결정 16 — 「환불 처리됨(운영자 확인)」 알림·메일) */
    public record RefundResolvedManually(long refundId, long participationId, long userId, String method) {
    }

    /** 공구가 마감·판정·중단됐다 */
    public record StatusChanged(long groupBuyId, String from, String to, String reason) {
    }
}
