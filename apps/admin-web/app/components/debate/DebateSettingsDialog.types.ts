import type { DebateItem, DebateSaveRequest } from '~/composables/content/api/useDebateApi.types';

/** 설정 대화창이 부르는 서버 동작(글 번호로) — `useDebateSettingsActions` 가 만든다. 실패하면 `ApiError` 를 던진다 */
export interface DebateSettingsActions {
    /** 그 글의 열린 토론 — 닫혀 있으면 404 `DEBATE_NOT_FOUND` */
    get: (postId: string) => Promise<DebateItem>;
    /** 열기·설정(200 한 줄) · 닫기(204 → null) */
    save: (postId: string, body: DebateSaveRequest) => Promise<DebateItem | null>;
}
