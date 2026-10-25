import type { DebateOpinion, DebateOpinionListView, DebateOpinionQuery, DebatePinRequest, DebatePinRequestListView } from '~/composables/content/api/useDebateApi.types';

/** 서랍이 다루는 글 — 토론 관리 줄·하이라이트 줄이 이 모양으로 넘긴다(목록을 받기 전 탭 머리의 수·중립 탭 판정에 쓴다) */
export interface DebateDrawerTarget {
    postId: string;
    /** 서랍 머리에 보일 제목(하이라이트는 노출 제목, 없으면 글 제목) */
    title: string;
    /** 「중립도 받기」 */
    neutralEnabled: boolean;
    /** 입장별 의견 수(목록을 받기 전 탭 머리) — 모르면 null(0 으로 보인다) */
    counts: { pro: number; con: number; neutral: number } | null;
}

/** 서랍을 열 때 처음 보일 탭 — 의견(찬성 탭) · 고정 요청(보기 권한 `canRead` 가 있을 때만, 없으면 의견) */
export type DebateDrawerInitialTab = 'OPINIONS' | 'PIN_REQUESTS';

/** 서랍이 부르는 서버 동작(글 번호로) — `useDebateOpinionActions` 가 만든다. 실패하면 `ApiError` 를 던진다 */
export interface DebateOpinionActions {
    load: (postId: string, query: DebateOpinionQuery) => Promise<DebateOpinionListView>;
    /** 고정(`on`) · 고정 풀기 — 고정하면 그 의견의 대기 고정 요청은 서버가 승인으로 닫는다 */
    pin: (postId: string, commentId: string, on: boolean) => Promise<DebateOpinion>;
    /** 숨김(`on`) · 숨김 풀기(운영자 숨김만 풀린다) */
    hide: (postId: string, commentId: string, on: boolean) => Promise<DebateOpinion>;
    /** 대기 중인 의견 고정 요청(오래된 것부터) — `post.read` */
    pinRequests: (postId: string) => Promise<DebatePinRequestListView>;
    /** 고정 요청 승인(그 의견을 고정) */
    approvePinRequest: (postId: string, requestId: string) => Promise<DebatePinRequest>;
    /** 고정 요청 거절 — 사유(선택, 0~100자)는 요청한 회원에게 알림으로 간다 */
    rejectPinRequest: (postId: string, requestId: string, reason: string | null) => Promise<DebatePinRequest>;
}
