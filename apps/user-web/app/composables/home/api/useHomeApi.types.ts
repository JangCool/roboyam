/**
 * 최신글 전체·인기글 전체 API 가 주고받는 모양 — **서버에 아직 없다**(목업). 모양은 community `docs/api-home.md`「최신글 전체」·`docs/api-popular.md` 5절 그대로다.
 * 계약 후보: `docs/features/member-activity-contract.md`. 글 한 줄은 게시판 목록 줄 + 게시판(`PostFeedRow`).
 * ⚠ ID 는 문자열(Snowflake), 시각은 UTC ISO.
 */
import type { PostFeedRow } from '~/composables/board/api/usePostApi.types';

/** `GET /api/home/latest?size&cursor` 요청 */
export interface LatestQuery {
    /** 1~50, 기본 20 */
    size?: number;
    cursor?: string;
}

/** 인기글 기간 탭 — 실시간(오늘 올라온 글) · 어제 · 이번 주 */
export type PopularPeriod = 'REALTIME' | 'YESTERDAY' | 'WEEK';

/** 인기글 순위가 모자라 다른 기간 글로 채운 줄의 출처 — 어제 글(실시간 탭) · 지난주 글(이번 주 탭) */
export type PopularFill = 'YESTERDAY' | 'LAST_WEEK';

/** 글 ID → 채운 줄의 출처 */
export type PopularFills = Record<string, PopularFill>;

/** 이번 주가 시작하는 요일(운영자 설정 — 서버가 정해 준다) */
export type WeekStart = 'MONDAY' | 'SUNDAY';

/** `GET /api/home/popular?period&page&size` 요청 */
export interface PopularQuery {
    period: PopularPeriod;
    /** 1부터 */
    page: number;
    /** 1~50, 기본 20 */
    size?: number;
}

/**
 * `GET /api/home/popular` 응답 — 게시판 목록과 같은 모양에 기간·채운 줄을 더한 것. 그 기간의 **순위 순**이고 순위 스냅샷(기본 50개)에 든 글만 나온다(쪽 번호).
 * `from`·`to`(이번 주 탭) — 그 주의 범위(서비스 시간대 달력, `to` 는 다음 주 시작 순간). 다른 탭은 null.
 */
export interface PopularListResponse {
    pinned: PostFeedRow[];
    items: PostFeedRow[];
    page: number;
    totalPages: number;
    totalCount: number;
    hasNext: boolean;
    nextCursor: null;
    period: PopularPeriod;
    fills: PopularFills;
    from: string | null;
    to: string | null;
    weekStart: WeekStart | null;
}
