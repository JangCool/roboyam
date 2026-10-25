import type { AdminPostRef } from '~/composables/content/api/usePopularApi.types';

/** 페이지가 `HighlightFormDialog` 에 내려 주는 서버 동작 — 실패하면 `ApiError` 를 던진다 */
export interface HighlightFormActions {
    /** 지정할 수 있는 글 찾기(글 번호 또는 제목 일부) */
    searchPosts: (q: string, signal?: AbortSignal) => Promise<AdminPostRef[]>;
    /** 대표 이미지 올리기 — 올린 파일 ID */
    uploadImage: (file: File) => Promise<string>;
}
