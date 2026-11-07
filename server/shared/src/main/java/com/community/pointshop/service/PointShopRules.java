package com.community.pointshop.service;

import com.community.pointshop.entity.PointShopPurchaseStatus;
import com.community.pointshop.entity.UserItemStatus;
import java.time.Duration;
import java.time.Instant;

/**
 * 포인트 상점 셀프 환불 판정 한 곳(commerce.md 7-3 D6, 화면 확정 「환불 기한 바꾸기」) — 모든 품목이 같은 규칙: <b>안 썼고</b>({@code first_used_at} 없음·아직 가짐)
 * <b>산 뒤 지금 설정의 {@code refund_days} 안</b>이면 셀프 환불. 이미 산 구매도 산 때가 아니라 <b>지금</b> 값으로 판정한다. 조건 밖은 운영자 예외 환불({@code pointshop.refund}).
 */
public final class PointShopRules {

    /** 셀프 환불이 안 되는 이유 — 썼다(장착·사용·프로필로 고름) */
    public static final String BLOCKED_USED = "USED";
    /** 셀프 환불이 안 되는 이유 — 기한이 지났다 */
    public static final String BLOCKED_EXPIRED = "EXPIRED";
    /** 셀프 환불이 안 되는 이유 — 올리기 변경권이 대기 신청에 예약됐다(commerce-motion-avatar-contract 규칙 0 — 반려·취소되면 풀린다) */
    public static final String BLOCKED_RESERVED = "RESERVED";
    /** 셀프 환불이 안 되는 이유 — 보상 이벤트로 받은 아이템(구매가 없다 — V163, event-admin-contract 「지급 아이템은 환불 대상이 아니다」) */
    public static final String BLOCKED_GRANTED = "GRANTED";

    private PointShopRules() {
    }

    /** 환불 기한 — 산 시각 + 지금 설정 일수(이 시각까지 포함) */
    public static Instant refundUntil(Instant purchasedAt, int refundDays) {
        return purchasedAt.plus(Duration.ofDays(refundDays));
    }

    /**
     * 셀프 환불이 안 되는 이유(되면 null). 회수된 줄·환불된 구매는 부르는 쪽이 먼저 「이미 환불」로 거른다.
     *
     * @return {@link #BLOCKED_USED} · {@link #BLOCKED_EXPIRED} · null
     */
    public static String selfRefundBlock(UserItemStatus status, Instant firstUsedAt, Instant purchasedAt, int refundDays, Instant now) {
        return selfRefundBlock(status, firstUsedAt, null, purchasedAt, refundDays, now);
    }

    /**
     * 셀프 환불이 안 되는 이유 — 예약 칸까지 본다(쓴 것 → 예약됨 → 기한 차례).
     *
     * @param reservedAt 올리기 변경권이 대기 신청에 예약된 시각(아니면 null)
     * @return {@link #BLOCKED_USED} · {@link #BLOCKED_RESERVED} · {@link #BLOCKED_EXPIRED} · null
     */
    public static String selfRefundBlock(UserItemStatus status, Instant firstUsedAt, Instant reservedAt, Instant purchasedAt, int refundDays, Instant now) {
        if (status != UserItemStatus.OWNED || firstUsedAt != null) {
            return BLOCKED_USED;
        }
        if (reservedAt != null) {
            return BLOCKED_RESERVED;
        }
        if (now.isAfter(refundUntil(purchasedAt, refundDays))) {
            return BLOCKED_EXPIRED;
        }
        return null;
    }

    /** 지금 셀프 환불할 수 있나(완료된 구매·가진 줄·안 씀·기한 안) */
    public static boolean selfRefundable(PointShopPurchaseStatus purchase, UserItemStatus status, Instant firstUsedAt, Instant purchasedAt, int refundDays, Instant now) {
        return selfRefundable(purchase, status, firstUsedAt, null, purchasedAt, refundDays, now);
    }

    /** 지금 셀프 환불할 수 있나 — 예약된 변경권은 안 된다 */
    public static boolean selfRefundable(PointShopPurchaseStatus purchase, UserItemStatus status, Instant firstUsedAt, Instant reservedAt, Instant purchasedAt,
                                         int refundDays, Instant now) {
        return purchase == PointShopPurchaseStatus.COMPLETED && status != UserItemStatus.REVOKED
                && selfRefundBlock(status, firstUsedAt, reservedAt, purchasedAt, refundDays, now) == null;
    }
}
