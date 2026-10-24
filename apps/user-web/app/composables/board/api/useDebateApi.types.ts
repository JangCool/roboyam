/**
 * 하이라이트 토론(찬성·반대 의견)이 주고받는 모양 — 서버 `server/api/.../debate/dto` 와 같다(docs/features/popular-highlight-contract.md 「서버 구현 결과」, community `api-debate.md`).
 * 글 상세의 `debate`·댓글 목록의 `debate`·댓글의 `stance` 도 여기 타입을 쓴다. ⚠ ID 는 모두 문자열이다.
 */
import type { PostAuthor } from '~/composables/board/api/usePostApi.types';

/** 입장 — 찬성 · 반대 */
export type DebateStance = 'PRO' | 'CON';

/** 댓글 목록 구분 — 원댓글의 입장으로 가른다(답글은 원댓글을 따라간다) */
export type CommentKind = 'ALL' | 'GENERAL' | 'PRO' | 'CON';

/** 글 상세(`GET /api/posts/{id}`)의 토론 요약 — 늘 있다(하이라이트가 아니면 `enabled false`) */
export interface DebateSummary {
    /** 지금 이 글이 찬성·반대 의견을 받는가(노출 중인 하이라이트 + 「찬성·반대 의견 받기」 켬) */
    enabled: boolean;
    /** 내가 이 글에서 정한 입장 — 정하지 않았거나 비로그인이면 null. 하이라이트가 끝나도 남는다 */
    myStance: DebateStance | null;
    /** 입장 배지가 붙는 댓글의 글자 수 한도 */
    opinionMax: number;
}

/** 글의 댓글 수 구분 — `all` 은 보이는 댓글 전체(답글 포함), 나머지는 지우거나 숨기지 않은 원댓글 수 */
export interface DebateCounts {
    all: number;
    general: number;
    pro: number;
    con: number;
}

/** 댓글 목록의 토론 칸 — 늘 있다. 화면은 `enabled || pro + con > 0` 일 때 구분 탭을 보인다(끝난 하이라이트는 읽기 전용) */
export interface CommentDebate extends DebateSummary {
    counts: DebateCounts;
}

/** `PUT /api/posts/{postId}/stance` — 요청과 응답이 같은 모양 */
export interface StanceBody {
    stance: DebateStance;
}

/** @ 지정 후보 한 명 — 고르면 `parentId`·`replyToId` 로 그 회원의 최근 댓글에 답글을 쓴다 */
export interface Commenter {
    author: PostAuthor;
    parentId: string;
    replyToId: string;
}

/** `GET /api/posts/{postId}/commenters?q=` — 최대 10명, 가장 최근에 쓴 회원 먼저 */
export interface CommenterList {
    items: Commenter[];
}
