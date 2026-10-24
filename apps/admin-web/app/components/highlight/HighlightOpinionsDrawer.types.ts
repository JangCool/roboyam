import type { HighlightOpinion, HighlightOpinionListView, HighlightOpinionQuery } from '~/composables/content/api/useHighlightApi.types';

/** 페이지가 `HighlightOpinionsDrawer` 에 내려 주는 서버 동작(그 하이라이트에 묶인 것) — 실패하면 `ApiError` 를 던진다 */
export interface HighlightOpinionActions {
    load: (query: HighlightOpinionQuery) => Promise<HighlightOpinionListView>;
    /** 고정(`on`) · 고정 풀기 */
    pin: (commentId: string, on: boolean) => Promise<HighlightOpinion>;
    /** 숨김(`on`) · 숨김 풀기(운영자 숨김만 풀린다) */
    hide: (commentId: string, on: boolean) => Promise<HighlightOpinion>;
}
