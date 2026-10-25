/**
 * 게시글 투표(설문)가 주고받는 모양 — 계약 `docs/features/post-poll-contract.md` 「API」 그대로(서버 `server/api` 의 poll DTO 와 같은 칸 이름).
 * 찬반 토론과는 별개 기능이고 한 글에 둘 다 있을 수 있다. 누가 무엇을 골랐는지는 어떤 응답에도 없다(익명).
 * ⚠ ID 는 모두 문자열이다. 시각은 시간대가 붙은 ISO 글자.
 */

/**
 * 글쓰기·고치기 본문의 `poll` — 질문 공백 정리 뒤 1~100자, 선택지 빈 칸 빼고 2~6개·각 1~50자·대소문자 무시 서로 다름,
 * `closesAt` 은 지금+1분 ~ 365일 안(null = 마감 없음). 오류는 `400 VALIDATION fields."poll.question"|"poll.options"|"poll.closesAt"`, 게시판 옵션이 꺼졌거나 비회원이면 `fields.poll`.
 */
export interface PollInput {
    question: string;
    options: string[];
    multiple: boolean;
    closesAt: string | null;
    /** 「다시 투표 허용」 — 켜면 마감 전까지 다른 선택으로 다시 투표할 수 있다(취소는 없다) */
    changeAllowed: boolean;
}

/** 지금 투표할 수 없는 까닭 — 마감 · 로그인 필요 · 이메일 인증 전 · 정지 */
export type PollVoteBlockedReason = 'CLOSED' | 'LOGIN_REQUIRED' | 'EMAIL_NOT_VERIFIED' | 'ACCOUNT_RESTRICTED';

export interface PollOption {
    id: string;
    label: string;
}

/** 선택지 하나의 득표 수 */
export interface PollResult {
    optionId: string;
    votes: number;
}

/**
 * 글 상세의 `poll`·`GET /api/posts/{id}/poll`·투표 응답 — 결과(`voterCount`·`results`)는 투표한 사람에게 바로, 마감되면 모두에게(`resultsVisible`). 그 전에는 null.
 */
export interface PollView {
    id: string;
    question: string;
    multiple: boolean;
    changeAllowed: boolean;
    closesAt: string | null;
    closed: boolean;
    options: PollOption[];
    /** 내가 이미 투표했다 */
    voted: boolean;
    /** 내가 고른 선택지 — 투표하지 않았거나 비로그인이면 빈 배열 */
    myOptionIds: string[];
    /** 로그인·이메일 인증·정지 아님·마감 전이고 (아직 투표 안 함 또는 `changeAllowed`) */
    canVote: boolean;
    voteBlockedReason: PollVoteBlockedReason | null;
    resultsVisible: boolean;
    /** 투표한 사람 수 — 결과를 볼 수 없으면 null */
    voterCount: number | null;
    /** 선택지별 득표 — 결과를 볼 수 없으면 null */
    results: PollResult[] | null;
    /** 글쓴이이고 첫 표 전 — 고치기·[투표 지우기]를 할 수 있다 */
    editable: boolean;
}

/** `POST /api/posts/{id}/poll/votes` — 단일 = 정확히 1개, 복수 = 1개 이상 */
export interface PollVoteBody {
    optionIds: string[];
}
