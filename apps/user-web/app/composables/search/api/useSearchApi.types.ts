/**
 * 통합 검색 API 가 주고받는 모양 — 서버 `search/dto/response/SearchResponse`·`SearchRowView` 와 같다(2026-10-24 연결). 계약: `docs/features/member-activity-contract.md`.
 * 글 한 줄은 게시판이 섞인 목록 줄(`PostFeedRow`) + 본문 일치 조각(`snippet`).
 * ⚠ ID 는 문자열(Snowflake), 시각은 UTC ISO.
 */
import type { PostFeedRow } from '~/composables/board/api/usePostApi.types';

/** 검색 범위 — 제목·본문·작성자 모두 · 제목 · 본문 · 작성자(닉네임) */
export type SearchScope = 'all' | 'title' | 'body' | 'author';

/** 정렬 — 최근 글이 위 · 오래된 글이 위 */
export type SearchOrder = 'latest' | 'oldest';

/** `GET /api/search` 요청 — 쪽 번호(`page`, 1~10)와 커서(`cursor`)를 함께 보내지 않는다 */
export interface SearchRequest {
    q: string;
    scope?: SearchScope;
    /** 게시판 slug — 없으면 모든 게시판 */
    board?: string;
    order?: SearchOrder;
    /** 1~50, 기본 20 */
    size?: number;
    page?: number;
    cursor?: string;
}

/** 검색 결과 한 줄 — 본문이 걸린 글만 `snippet`(일치한 자리 앞뒤 글자 약 100자, 공백은 한 칸 — 앞뒤가 잘린 쪽에는 서버가 「…」를 붙인다)이 있다 */
export interface SearchRow extends PostFeedRow {
    snippet: string | null;
}

/**
 * `GET /api/search` 응답. `query`·`tokens` 는 서버가 다듬은 검색어·단어(화면이 일치 부분을 강조한다).
 * 쪽 번호로 물으면 `page`·`totalPages`(최대 10, 결과가 없으면 0)가 오고, 커서로 물으면 둘 다 null 이다.
 * 오류: `400 VALIDATION` `fields.q|scope|board|order|page|cursor`(없는·꺼진 게시판도 `fields.board`) · `503 SEARCH_TIMEOUT`(3초를 넘김 — 더 구체적인 검색어로).
 */
export interface SearchResponse {
    query: string;
    tokens: string[];
    scope: SearchScope;
    board: string | null;
    items: SearchRow[];
    page: number | null;
    totalPages: number | null;
    hasNext: boolean;
    nextCursor: string | null;
}
