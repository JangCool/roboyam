/**
 * 글 태그(관리자) 주고받는 모양 — 계약 `docs/features/post-tags-contract.md` 「API 후보」. 서버 DTO 가 이 칸 이름 그대로다.
 */

/** 태그 추천 한 줄 — 태그(처음 쓴 모양)와 기간 안 글에 달린 수 */
export interface TagCount {
    name: string;
    count: number;
}

/** `GET /api/admin/tags/popular` 조건 */
export interface TagPopularQuery {
    /** 게시판 slug 들 — 비우면 전체 게시판 */
    boards: string[];
    /** 최근 며칠 — 계약 기본 30 */
    days: number;
    /** 최대 몇 개 — 계약 기본 30 */
    limit: number;
}
