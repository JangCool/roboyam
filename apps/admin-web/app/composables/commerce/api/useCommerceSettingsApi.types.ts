/**
 * 커머스 설정 API 가 주고받는 모양 — 서버 DTO `RequestLimitsView`·`RequestLimitsSaveRequest`(server/admin `commerce.dto`).
 * 정책 원천: `docs/domain/commerce.md` 12-4(돈 관련 요청 속도 제한). 시각은 UTC ISO 다 — 화면은 `useDisplayTime()` 으로 그린다.
 */

/** 제한하는 요청 종류 — 화면·서버 모두 이 차례로 보인다 */
export type RequestLimitAction = 'GROUPBUY_JOIN' | 'POINT_CHARGE' | 'SHOP_PURCHASE' | 'REFUND_REQUEST';

/** 고정 차례(공구 참여 · 충전 요청 · 상점 구매 · 환불 신청) — 타입에 딸린 상수 */
export const REQUEST_LIMIT_ACTIONS: readonly RequestLimitAction[] = ['GROUPBUY_JOIN', 'POINT_CHARGE', 'SHOP_PURCHASE', 'REFUND_REQUEST'];

export interface RequestLimitItem {
    action: RequestLimitAction;
    /** 회원 한 명이 1분 동안 보낼 수 있는 수(0 = 제한 없음, 0~100) */
    limitPerMinute: number;
    /** 처음 값(12-4 표) — 「기본값 N」 표시와 되돌리기에 쓴다 */
    defaultLimit: number;
}

/** `GET`·`PUT /api/admin/commerce/request-limits` 응답 */
export interface RequestLimitsView {
    items: RequestLimitItem[];
    /** 마지막 저장 시각(UTC ISO). 저장한 적 없으면 null(기본값) */
    updatedAt: string | null;
    /** 마지막으로 저장한 운영자 이름. 저장한 적 없으면 null */
    updatedBy: string | null;
}

/** `PUT /api/admin/commerce/request-limits` 요청 — 네 종류 모두 보낸다 */
export interface RequestLimitsSaveRequest {
    limits: Record<RequestLimitAction, number>;
}
