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

/**
 * AI 연애상담소 글(ai-love-counsel-contract)의 일반 댓글 구획 = 「회원 답변」 — 제목을 「회원 답변 n」으로, 원댓글마다 「이 조언 고르기」·「가장 도움 된 조언」.
 * 위험 사연은 넘기지 않는다(그냥 댓글). 고르기는 페이지가 확인창 뒤 한다(`pick` 이벤트)
 */
export interface CommentCounselPick {
    /** 사연 쓴 사람이고 아직 고르지 않았다(서버 `canPick`) */
    canPick: boolean;
    /** 고른 답의 댓글 ID(AI 상담사 답일 수도 있다) */
    pickedId: string | null;
    /** 고르는 중인 댓글 ID(없으면 빈 글자) */
    busyId: string;
}
