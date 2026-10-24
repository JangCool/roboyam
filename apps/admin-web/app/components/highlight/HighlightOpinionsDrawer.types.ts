import type { HighlightOpinion, HighlightOpinionListView, HighlightOpinionQuery, HighlightPinRequest, HighlightPinRequestListView } from '~/composables/content/api/useHighlightApi.types';

/** 페이지가 `HighlightOpinionsDrawer` 에 내려 주는 서버 동작(그 하이라이트에 묶인 것) — 실패하면 `ApiError` 를 던진다 */
export interface HighlightOpinionActions {
    load: (query: HighlightOpinionQuery) => Promise<HighlightOpinionListView>;
    /** 고정(`on`) · 고정 풀기 — 고정하면 그 의견의 대기 고정 요청은 서버가 승인으로 닫는다 */
    pin: (commentId: string, on: boolean) => Promise<HighlightOpinion>;
    /** 숨김(`on`) · 숨김 풀기(운영자 숨김만 풀린다) */
    hide: (commentId: string, on: boolean) => Promise<HighlightOpinion>;
    /** 대기 중인 의견 고정 요청(오래된 것부터) — 서버가 `highlight.manage` 를 요구한다 */
    pinRequests: () => Promise<HighlightPinRequestListView>;
    /** 고정 요청 승인(그 의견을 고정) */
    approvePinRequest: (requestId: string) => Promise<HighlightPinRequest>;
    /** 고정 요청 거절 — 사유(선택, 0~100자)는 요청한 회원에게 알림으로 간다 */
    rejectPinRequest: (requestId: string, reason: string | null) => Promise<HighlightPinRequest>;
}
