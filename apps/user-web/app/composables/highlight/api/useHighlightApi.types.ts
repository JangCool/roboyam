import type { HomeHighlight } from '~/composables/gadget/api/useGadgetApi.types';
import type { WeekStart } from '~/composables/home/api/useHomeApi.types';

/**
 * 「아추!」(아이로빗 추천) 페이지의 기간 — 운영자가 하이라이트로 **고른 날**(노출 시작) 기준 오늘 · 이번 주 · 이번 달(사용자 2026-11-19 결정 A).
 * 주 시작 요일은 인기글 설정의 「이번 주 시작 요일」을 따른다(서버가 정한다)
 */
export type AchuPeriod = 'DAY' | 'WEEK' | 'MONTH';

/** `GET /api/highlights?period&page&size` 요청(서버 후보 — achu-page-contract) */
export interface AchuQuery {
    period: AchuPeriod;
    /** 1부터 */
    page: number;
    /** 1~50, 기본 20 */
    size?: number;
}

/** 아추! 한 줄 — 홈 하이라이트 가젯의 글(`HomeHighlight`)에 고른 시각을 더한 것 */
export interface AchuItem extends HomeHighlight {
    /** 하이라이트로 고른 시각(노출 시작, ISO-8601 UTC) — 기간 묶기의 기준 */
    pickedAt: string;
}

/**
 * `GET /api/highlights` 응답 — 그 기간에 고른 노출 중인 하이라이트를 **고른 시각 최신순**으로(가정 — 운영자 순서로 바꿀 수 있다), 쪽 번호.
 * `from`·`to` — 그 기간의 범위(서비스 시간대 달력, `to` 는 다음 기간 시작 순간). `totalPages` 는 최소 1.
 */
export interface AchuListResponse {
    items: AchuItem[];
    page: number;
    totalPages: number;
    totalCount: number;
    hasNext: boolean;
    period: AchuPeriod;
    from: string;
    to: string;
    weekStart: WeekStart;
}
