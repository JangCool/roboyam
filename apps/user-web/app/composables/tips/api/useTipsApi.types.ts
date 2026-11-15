/**
 * 팁·강좌·사용기 묶음 화면(`/tips`)이 주고받는 모양 — 서버 `server/api` `tips.dto.response`(`TipsOverviewView`·`TipsPostPageView`·`TipsPostView`)와 같다(2026-11-07 연결).
 * 글 한 줄은 게시판이 섞인 목록 줄(`PostFeedRow`) 칸 그대로 + 본문 요약(`excerpt`). 계약 `docs/features/tips-contract.md`.
 * 태그(`TipsPost.tags`·`TipsOverview.tags`)는 글 태그(post-tags-contract)의 실제 값이다 — 옛 서버라 칸이 없으면 화면이 빈 값으로 읽는다(`UtilTips.overviewOf`).
 * ⚠ ID 는 모두 문자열이다(Snowflake 64비트). 숫자로 바꾸지 않는다.
 */
import type { PostBoardRef, PostFeedRow } from '~/composables/board/api/usePostApi.types';

/** 묶음에 든 게시판 — 섞인 목록의 게시판 표시 + 게시판 ID */
export interface TipsBoardRef extends PostBoardRef {
    id: string;
}

/** 묶음 화면의 글 한 줄 */
export interface TipsPost extends PostFeedRow {
    /**
     * 본문 평문 앞 약 120자(공백 정리, 넘으면 「…」) — 가려진 글·19금 글·본문이 빈 글은 null.
     * 헤드라인의 하이라이트 줄은 운영자가 적은 요약이 있으면 그것(노출 제목·대표 이미지도 `title`·`thumbnailUrl` 에 들어온다)
     */
    excerpt: string | null;
    /** 글 태그(처음 쓴 모양, 최대 5개) — 없으면 빈 배열 */
    tags: string[];
}

/** 태그 칩 하나 — 묶음 게시판 글에 최근 달린 횟수 */
export interface TipsTag {
    name: string;
    count: number;
}

/**
 * 묶음 머리 몫 `GET /api/tips?board=` — 칩에 그릴 게시판들 · 헤드라인(큰 카드 1 + 작은 카드 4) · 많이 본 글 1~5.
 * `board` 가 비었거나 묶음에 없는 값이면 전체 묶음 기준이다.
 */
export interface TipsOverview {
    /** 묶음에 든 켜진 게시판(게시판 차례 — 칩 순서). `board` 로 하나를 골라도 묶음 전체 */
    boards: TipsBoardRef[];
    /**
     * 「이번 주 추천」 최대 5개 — 운영자가 `tips` 곳에 고른 하이라이트(관리자 순서) 먼저, 모자라면 최근 7일 추천순, 그래도 모자라면 더 오래된 글 추천순. 첫 글이 큰 카드.
     * 게시판을 고르지 않으면 운영자가 고른 글은 묶음 밖 게시판 글이어도 들어온다
     */
    headline: TipsPost[];
    /** 많이 본 글 최대 5개 — 최근 30일 조회순, 모자라면 더 오래된 글 조회순(헤드라인과 겹칠 수 있다) */
    mostViewed: TipsPost[];
    /** 묶음 게시판(tip·review)에서 최근 많이 달린 태그 위 10개(많은 차례) — 칩. 가려진·삭제된 글은 세지 않는다(서버) */
    tags: TipsTag[];
}

/** 최신 글 목록 `GET /api/tips/posts?board=&tag=&page=` */
export interface TipsPostQuery {
    /** 게시판 slug — 빈 글자 = 전체 */
    board: string;
    /** 태그 거르기(정확히 같은 태그, 대소문자 무시) — 빈 글자 = 거르지 않음 */
    tag: string;
    /** 1부터 */
    page: number;
}

/** 최신 글 한 쪽 — 한 쪽 20개, 묶음(또는 고른 게시판) 글을 서버가 최신순으로 합친다 */
export interface TipsPostPage {
    items: TipsPost[];
    page: number;
    /** 글이 없으면 0 */
    totalPages: number;
    /** 공지 제외, 신고로 숨긴 글 자리 포함(게시판 글 목록과 같은 셈) */
    totalCount: number;
}
