import type { ContentFormat, GuestCredentials } from '~/composables/board/api/usePostApi.types';

/** 페이지가 `CommentSection` 에 내려 주는 댓글 동작 — `usePostComments` 의 함수들. 실패하면 `ApiError` 를 던진다 */
export interface CommentActions {
    create: (content: string, contentFormat: ContentFormat, target: { parentId: string | null; replyToId: string | null }, guest: GuestCredentials | null) => Promise<unknown>;
    update: (id: string, content: string, contentFormat: ContentFormat, password: string | null) => Promise<void>;
    remove: (id: string, password: string | null) => Promise<void>;
    like: (id: string) => Promise<void>;
}
