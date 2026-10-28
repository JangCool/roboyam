package com.community.groupbuy.dto;

import com.community.groupbuy.entity.QtyMovementKind;
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

    /** 공구가 마감·판정·중단됐다 */
    public record StatusChanged(long groupBuyId, String from, String to, String reason) {
    }
}
