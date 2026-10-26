package com.community.point.entity;

/**
 * 원장 줄의 종류(V85 {@code ck_point_ledger_kind}) — docs/domain/commerce.md 5-3. 부호 규칙: {@link #EARN}·{@link #CANCEL_USE} 면 +, 나머지는 −(DB CHECK 가 막는다).
 */
public enum PointKind {

    /** 지급(활동 적립·운영 지급(+)·글 복구 되돌림·충전 등) */
    EARN,
    /** 사용(포인트 상점 구매·공구 포인트 결제) */
    USE,
    /** 회수 — 무상에서만(글·댓글 삭제·운영 차감·미회수 상계) */
    REVOKE,
    /** 사용 취소 — 원래 USE 줄이 뺀 묶음으로 되돌린다 */
    CANCEL_USE,
    /** 소멸(유효기간 만료·탈퇴 포기) — 대상은 묶음 */
    EXPIRE,
    /** 유상 환불(충전 환불 — 3단계) */
    REFUND;

    /** 이 종류의 줄은 잔액을 늘리는가 */
    public boolean credit() {
        return this == EARN || this == CANCEL_USE;
    }
}
