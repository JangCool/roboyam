package com.community.point.entity;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * 원장 한 줄의 사유 코드 — V85 의 {@code ck_point_ledger_reason}(V80 목록 ∪ docs/domain/commerce.md 5-3 의 값 전부)과 같은 값이다. admin-web {@code PointReasonCode} 와도 같다.
 * 이름(화면에 보이는 「글 작성」 등)은 번들 키 {@code point.reason.<코드>} 로 요청 언어에 맞춰 서버가 준다.
 *
 * <p><b>지금 실제로 생기는 줄</b>: {@code POST}·{@code COMMENT}(작성) · {@code POST_REVOKE}·{@code COMMENT_REVOKE}(삭제 회수) · {@code LIKE_BONUS}·{@code LIKE_BONUS_REVOKE}
 * (추천 받음 보너스·삭제 회수) · {@code ADMIN_ADJUST}(운영 조정) · {@code POST_RESTORE}(운영자 복구로 되돌려 줌) · {@code REVOKE_DEBT_OFFSET}(미회수 상계) · {@code EXPIRE}(소멸).
 * 포인트 상점(1-2)이 {@code POINT_SHOP_PURCHASE}·{@code POINT_SHOP_REFUND} 를 쓴다. 출석·인기글·충전·공구·탈퇴 사유는 그 기능이 생길 때 줄이 생긴다.
 * {@code TICKET_PURCHASE} 는 CHECK 에만 남고 쓰지 않는다(세션 정리 결정 3 — 구매는 모두 {@code POINT_SHOP_PURCHASE}).
 */
public enum PointReason {

    ATTENDANCE,
    /** 연속 출석 보너스(출석 기능을 만들 때 — commerce.md 6-2) */
    ATTENDANCE_STREAK,
    POST,
    COMMENT,
    POPULAR,
    POST_REVOKE,
    COMMENT_REVOKE,
    TICKET_PURCHASE,
    ADMIN_ADJUST,
    DEAL_END_REQUEST,
    LIKE_BONUS,
    LIKE_BONUS_REVOKE,
    SIGNUP,
    /** 운영자가 지운 글을 복구 — 그 글로 회수했던 포인트를 회수한 묶음으로 되돌려 준다(V80, 결정 7) */
    POST_RESTORE,
    /** 충전(유상, 3단계) */
    CHARGE,
    /** 충전 보너스(무상, 3단계 — 미회수 상계 대상이 아니다) */
    CHARGE_BONUS,
    CHARGE_BONUS_REVOKE,
    CHARGE_REFUND,
    CHARGE_REFUND_RESTORE,
    CHARGE_BONUS_RESTORE,
    /** 포인트 상점 구매(사용 — 1-2) */
    POINT_SHOP_PURCHASE,
    /** 포인트 상점 환불(사용 취소 — 원래 묶음으로) */
    POINT_SHOP_REFUND,
    GB_POINT_USE,
    GB_POINT_RESTORE,
    /** 미회수 상계 — 적립 직후 같은 트랜잭션에서 빚만큼 무상에서 뺀다(대상 = 적립 줄 {@code LEDGER}) */
    REVOKE_DEBT_OFFSET,
    /** 유효기간 만료 소멸(대상 = 묶음 {@code LOT}) */
    EXPIRE,
    /** 탈퇴로 포기·소멸(17-2) */
    WITHDRAW_FORFEIT;

    /** 묶음 출처 전용 코드 — 기존 잔액을 전환한 묶음(V85, D4). 원장 줄이 없으므로 원장 사유가 아니다. 이름은 번들 {@code point.reason.MIGRATION} */
    public static final String MIGRATION_LOT_SOURCE = "MIGRATION";

    private static final Set<PointReason> USES = EnumSet.of(TICKET_PURCHASE, POINT_SHOP_PURCHASE, GB_POINT_USE);
    private static final Set<PointReason> CANCEL_USES = EnumSet.of(POINT_SHOP_REFUND, GB_POINT_RESTORE);
    private static final Set<PointReason> EXPIRES = EnumSet.of(EXPIRE, WITHDRAW_FORFEIT);
    private static final Set<PointReason> REFUNDS = EnumSet.of(CHARGE_REFUND);
    private static final Set<PointReason> REVOKES = EnumSet.of(POST_REVOKE, COMMENT_REVOKE, LIKE_BONUS_REVOKE, CHARGE_BONUS_REVOKE, REVOKE_DEBT_OFFSET);

    /** 원장 종류 — 사유마다 정해져 있고({@code ck_point_ledger_reason_kind}) 운영 조정만 부호로 지급·회수 */
    public PointKind kind(long delta) {
        if (USES.contains(this)) {
            return PointKind.USE;
        }
        if (CANCEL_USES.contains(this)) {
            return PointKind.CANCEL_USE;
        }
        if (EXPIRES.contains(this)) {
            return PointKind.EXPIRE;
        }
        if (REFUNDS.contains(this)) {
            return PointKind.REFUND;
        }
        if (REVOKES.contains(this)) {
            return PointKind.REVOKE;
        }
        if (this == ADMIN_ADJUST) {
            return delta > 0 ? PointKind.EARN : PointKind.REVOKE;
        }
        return PointKind.EARN;
    }

    /** {@link #kind(long)} 의 이름 */
    public String kindOf(long delta) {
        return kind(delta).name();
    }

    public String labelKey() {
        return "point.reason." + name();
    }

    public static Optional<PointReason> of(String code) {
        return Arrays.stream(values()).filter(reason -> reason.name().equals(code)).findFirst();
    }
}
