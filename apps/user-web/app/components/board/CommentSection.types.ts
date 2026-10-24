import type { CommenterList, DebateStance } from '~/composables/board/api/useDebateApi.types';
import type { ContentFormat, GuestCredentials } from '~/composables/board/api/usePostApi.types';

/** 페이지가 `CommentSection` 에 내려 주는 댓글 동작 — `usePostComments` 의 함수들. 실패하면 `ApiError` 를 던진다 */
export interface CommentActions {
    /** `stance` — 하이라이트 토론의 입장(회원·열린 토론만). 비우면 일반 댓글이거나 서버가 내 입장을 붙인다 */
    create: (
        content: string,
        contentFormat: ContentFormat,
        target: { parentId: string | null; replyToId: string | null },
        guest: GuestCredentials | null,
        stance: DebateStance | null,
    ) => Promise<unknown>;
    update: (id: string, content: string, contentFormat: ContentFormat, password: string | null) => Promise<void>;
    remove: (id: string, password: string | null) => Promise<void>;
    like: (id: string) => Promise<void>;
    /** 내 입장 바꾸기(이미 정한 회원만) — 바뀐 입장 */
    changeStance: (stance: DebateStance) => Promise<DebateStance>;
    /** @ 지정 후보(이 글에 댓글을 쓴 회원) */
    commenters: (q: string, signal?: AbortSignal) => Promise<CommenterList>;
}
