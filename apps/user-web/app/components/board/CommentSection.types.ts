import type { CommenterList, DebateStance, PinRequestReviewList, StanceChangeResult } from '~/composables/board/api/useDebateApi.types';
import type { ContentFormat, GuestCredentials } from '~/composables/board/api/usePostApi.types';

/** 페이지가 `CommentSection` 에 내려 주는 댓글 동작 — `usePostComments` 의 함수들. 실패하면 `ApiError` 를 던진다 */
export interface CommentActions {
    /** `opinionStance` — 토론 의견으로 쓸 때 내 지금 입장(`opinion: true` 로 보낸다). 일반 댓글·답글은 null */
    create: (
        content: string,
        contentFormat: ContentFormat,
        target: { parentId: string | null; replyToId: string | null },
        guest: GuestCredentials | null,
        opinionStance: DebateStance | null,
    ) => Promise<unknown>;
    update: (id: string, content: string, contentFormat: ContentFormat, password: string | null) => Promise<void>;
    remove: (id: string, password: string | null) => Promise<void>;
    like: (id: string) => Promise<void>;
    dislike: (id: string) => Promise<void>;
    undislike: (id: string) => Promise<void>;
}

/** 토론 구획(`variant="debate"`)에만 쓰는 동작 */
export interface DebateActions {
    /** 입장 고르기(투표) — 처음 고르기·바꾸기 모두. 성공하면 목록을 다시 받은 뒤 돌아온다 */
    vote: (stance: DebateStance) => Promise<StanceChangeResult>;
    /** @ 지정 후보(이 글에 의견을 쓴 회원) */
    commenters: (q: string, signal?: AbortSignal) => Promise<CommenterList>;
    /** 내 의견 고정 요청 · 취소 */
    requestPin: (commentId: string) => Promise<void>;
    cancelPin: (commentId: string) => Promise<void>;
    /** 확인 권한자 — 대기 요청 목록 · 승인 · 거절(성공하면 목록을 다시 받은 뒤 돌아온다) */
    pinRequests: () => Promise<PinRequestReviewList>;
    approvePin: (id: string) => Promise<void>;
    rejectPin: (id: string, reason: string | null) => Promise<void>;
}
