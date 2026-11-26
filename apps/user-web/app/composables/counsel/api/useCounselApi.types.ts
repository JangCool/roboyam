/**
 * AI 연애상담소(연애·결혼 게시판 「AI 상담소」 말머리 — `docs/features/ai-love-counsel-contract.md` 「API」)가 주고받는 모양 — 서버 `com.community.counsel.dto` 와 같다.
 * AI 상담사 답은 그 글의 댓글(작성자 = 상담 전용 AI 회원, `author.ai`, 아바타 없음 — 기본 아바타)이다 — 댓글 모양(`CommentView`)을 그대로 쓴다.
 * 회원 답변은 일반 댓글 목록 그대로(서버가 상담사 답을 그 목록·`total` 에서 뺀다).
 * 글 줄의 상태(`CounselStatus`·`PostCounselRow`)는 글 줄의 주인(`usePostApi.types.ts`)에 있다.
 * ⚠ ID 는 모두 문자열이다(Snowflake 64비트). 시각은 UTC ISO 글자.
 */
import type { CommentView } from '~/composables/board/api/useCommentApi.types';
import type { AuthorAi, CounselStatus } from '~/composables/board/api/usePostApi.types';

/** AI 상담사 한 명과 그 답 */
export interface CounselAiAnswer {
    counselorId: string;
    /** 상담사 이름(운영자가 정하는 페르소나 이름 — 「팩폭언니」 등) */
    name: string;
    /** 상담 성향 한 마디(운영자가 정하는 값 — 「직설형」·「공감형」·「현실 분석형」) */
    style: string;
    /** 답이 있으면 그 답을 만든 모델, 아직이면 계정의 지금 모델 — 서버 값 그대로 보인다 */
    ai: AuthorAi;
    /** 그 상담사의 답(댓글) — 아직 쓰는 중이면 null */
    comment: CommentView | null;
}

/**
 * `GET /api/posts/{id}/counsel` — 상담 글 상세의 AI 상담 칸. 「AI 상담소」 말머리 글이 아니면 404 `COUNSEL_NOT_FOUND`.
 * 위험 사연(`RISK`)이면 `counselors` 가 비어 오고 `canPick` 은 false(AI 가 답하지 않고, 조언 고르기도 하지 않는다).
 * `counselors` = 판정 전이면 지금 켜진 상담사, 판정 뒤면 답하기로 한 상담사(끝내 못 쓴 상담사는 글 + 60분 뒤 빠진다).
 */
export interface PostCounselView {
    status: CounselStatus;
    /** 보는 사람이 사연을 쓴 사람 · 판정을 마친 위험 아닌 사연(판정 전 1분 안은 false) · 아직 고르지 않았다 — 「이 조언 고르기」 단추(판정은 서버) */
    canPick: boolean;
    /** 「가장 도움 된 조언」으로 고른 답(AI 상담사 답 또는 회원 답의 댓글 ID) — 없으면 null */
    pickedCommentId: string | null;
    /** AI 답이 오는 시한(사연 등록 + 10분, 위험 사연은 null) — 지났는데 답이 없으면 화면은 「조금 늦어지고 있어요」 */
    answerDueAt: string | null;
    counselors: CounselAiAnswer[];
}

/** `PUT /api/posts/{id}/counsel/pick` 본문 — 응답은 바뀐 `PostCounselView`. 다시 고르기는 없다 */
export interface CounselPickRequest {
    commentId: string;
}

/** 순위 한 줄 — 사람 회원 전체가 한 줄(`HUMAN`), AI 상담사는 한 명이 한 줄(`AI`) */
export interface CounselRankRow {
    rank: number;
    kind: 'HUMAN' | 'AI';
    /** AI 상담사 이름(사람 회원 줄은 null — 화면이 「사람 회원」) */
    name: string | null;
    ai: AuthorAi | null;
    /** 이번 주 고른 수 · 비율(0~100, 서버가 큰 나머지 반올림 — 합이 100 이 되게. 고른 것이 없으면 모두 0) */
    picks: number;
    percent: number;
}

/** `GET /api/boards/{slug}/counsel/ranking` — 이번 주(월~일, 한국 시각) 「가장 도움 된 조언」으로 고른 수 기준. 상담소 말머리가 없는 게시판은 404 `COUNSEL_NOT_FOUND` */
export interface CounselRanking {
    /** 집계 구간(YYYY-MM-DD, 한국 날짜) */
    weekStart: string;
    weekEnd: string;
    totalPicks: number;
    rows: CounselRankRow[];
}
