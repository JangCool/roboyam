/**
 * 방문 분석(`/analytics/visits`) — `GET /api/admin/analytics/visits/**` 의 모양(서버 DTO 와 같은 모양 — 이 파일이 원천).
 * 계약 `docs/features/site-analytics-contract.md`. 날짜는 모두 한국 날짜(`YYYY-MM-DD`, 양끝 포함), 시각은 UTC ISO.
 *
 * 원천(성능): 요약·추이·요일·시간대 = `analytics_visit_hourly` · 지금 접속 = `analytics_visit_active` · 페이지 순위·서랍 일별 = `analytics_page_daily` · 유입 분류·도메인·일별 = `analytics_referrer_daily` ·
 * 캠페인 = 일별 캠페인 집계 표 · 이동 흐름·서랍 들어온/나간 곳 = `analytics_flow_daily` — 모두 집계 표. **원본(`analytics_page_views`)을 읽는 것은 실시간의 분당·최근 5분 칸(최근 30분)** 뿐이다(원본 기록을 켰을 때만).
 */
import type { DashboardPageKind, DashboardReferrerGroup } from '~/composables/dashboard/api/useDashboardGadgetApi.types';

/** 화면 종류 — 대시보드 「인기 페이지」와 같다(서버가 경로로 가른다, 모르는 화면은 `OTHER`) */
export type VisitPageKind = DashboardPageKind;

/** 유입처 분류 — 대시보드 「유입 경로」와 같다 */
export type VisitReferrerGroup = DashboardReferrerGroup;

/** 페이지 순위의 화면 종류 거르기 — 화면 차례 */
export const VISIT_PAGE_KINDS = ['HOME', 'BOARD', 'POST', 'GROUP_BUY', 'HOTDEAL', 'GROUP', 'POINT_SHOP', 'SEARCH', 'MY', 'OTHER'] as const satisfies readonly VisitPageKind[];

/** 유입 분류 — 화면 차례(서버가 빠뜨린 분류는 0 으로 채워 그린다) */
export const VISIT_REFERRER_GROUPS = ['SEARCH', 'SOCIAL', 'DIRECT', 'OTHER_SITE', 'CAMPAIGN'] as const satisfies readonly VisitReferrerGroup[];

/** 기간 — 한국 날짜, 양끝 포함 */
export interface VisitRange {
    from: string;
    to: string;
}

/** 추이 단위 — 하루 기간이면 시간별, 여러 날이면 일별 */
export type VisitGranularity = 'HOUR' | 'DAY';

/**
 * 방문 숫자.
 * `visitors` = **날마다 센 순방문자의 합**(하루 기간이면 그날 순방문자, 여러 날이면 같은 사람이 이틀 오면 2 — 기간 전체 순방문자가 아니다).
 * `newVisitors` = 처음 온 방문자(브라우저 방문자 ID 기준). `pageViews` = 본 횟수(새로 고침 포함).
 */
export interface VisitCounts {
    visitors: number;
    newVisitors: number;
    pageViews: number;
}

/** 직전 같은 길이 기간의 숫자 — 기간에 오늘이 끼면 직전 기간도 「같은 시각까지」(시간 단위)로 자른다 */
export interface VisitPreviousCounts extends VisitCounts {
    range: VisitRange;
}

/** 추이 한 칸 — `HOUR` 면 `at` = 정시 시각(ISO), `DAY` 면 한국 날짜(`YYYY-MM-DD`). 빈 칸 없이 오래된 차례(오늘은 지금 시각의 칸까지) */
export interface VisitSeriesPoint {
    at: string;
    visitors: number;
    newVisitors: number;
    pageViews: number;
}

/**
 * 수집 스위치(지금 값) — 각 칸은 「방문 수집 전체(`enabled`) && 그 항목」으로 서버가 접어서 준다.
 * 꺼진 항목의 구획은 「수집이 꺼져 있어요」(화면은 그 구획의 API 를 부르지 않는다).
 */
export interface VisitCollect {
    /** 방문 수집 전체 — 꺼지면 아래가 모두 false(설정 링크는 전체 스위치로) */
    enabled: boolean;
    /** 요약·추이 */
    visits: boolean;
    /** 페이지 순위(`analytics_page_daily` 는 원본 기록과 함께 쌓인다) · 실시간 분당·최근 5분(원본) */
    rawEvents: boolean;
    /** 유입 분류·도메인·일별 · 캠페인 표(일별 캠페인 집계 표) */
    referrers: boolean;
    /** 이동 흐름 */
    flows: boolean;
}

/** `GET /api/admin/analytics/visits/summary?from&to` */
export interface VisitSummary {
    range: VisitRange;
    /** 센 시각(ISO) — 「HH:mm 기준」(서버가 1분마다 몰아 쓰므로 최대 1분 늦다) */
    asOf: string;
    granularity: VisitGranularity;
    totals: VisitCounts;
    previous: VisitPreviousCounts;
    series: VisitSeriesPoint[];
    collect: VisitCollect;
    /** 지금 접속(최근 5분, `analytics_visit_active` COUNT — 기간과 무관) */
    activeNow: number;
}

/** 페이지 한 줄 — `title` = 글이면 글 제목, 아니면 화면 이름(게시판·공구·소모임 이름 …). 서버가 모르면 null(화면은 경로) */
export interface VisitPageRow {
    path: string;
    kind: VisitPageKind;
    title: string | null;
    titleEn: string | null;
    views: number;
}

/** `GET …/visits/pages?from&to&kind&q&page&size` 조회수 차례 한 쪽 */
export interface VisitPagePage {
    items: VisitPageRow[];
    /** 거른 페이지 수(쪽 나눔) */
    total: number;
    /** 거른 범위의 조회수 합(비율 막대의 분모) */
    totalViews: number;
}

export interface VisitPageQuery extends VisitRange {
    /** 화면 종류 — 없으면 전체 */
    kind?: VisitPageKind;
    /** 경로·제목 일부(이동 흐름의 기준 페이지 찾기) — 없으면 전체 */
    q?: string;
    /** 0부터 */
    page: number;
    size: number;
}

/** 유입 도메인 한 줄 */
export interface VisitReferrerDomain {
    domain: string;
    visits: number;
}

/** 캠페인 한 줄(`utm_source`·`utm_medium`·`utm_campaign` 묶음 — 빈 칸은 null) */
export interface VisitCampaignRow {
    source: string | null;
    medium: string | null;
    campaign: string | null;
    visits: number;
}

/** 분류별 일별 방문 한 칸(오래된 차례, 빈 날 없이 — 없는 분류는 0) */
export interface VisitReferrerSeriesPoint {
    date: string;
    groups: Record<VisitReferrerGroup, number>;
}

export interface VisitReferrerQuery extends VisitRange {
    /** false 면 분류·사이트만(일별 추이·캠페인은 빈 배열 — 개요의 미리보기). 기본 true */
    detail?: boolean;
}

/** `GET …/visits/referrers?from&to&detail` — 방문 = 세션 첫 페이지 수 */
export interface VisitReferrers {
    /** 분류 합 */
    total: number;
    groups: { group: VisitReferrerGroup; visits: number }[];
    /** 상위 10(직접·캠페인은 도메인이 없어 빠진다) */
    domains: VisitReferrerDomain[];
    /** 상위 20(일별 캠페인 집계 표를 기간으로 묶음 — 유입 수집이 켜져 있으면 원본 기록과 무관하게 있다) */
    campaigns: VisitCampaignRow[];
    /** 분류별 일별 방문(`analytics_referrer_daily` 를 날·분류로 묶음) */
    series: VisitReferrerSeriesPoint[];
}

/** 요일·시간대 한 칸 — `weekday` 1(월)~7(일), `hour` 0~23(한국 시각). 숫자는 기간 안의 같은 요일·시 칸들의 **합**, `days` = 더한 칸 수(평균 = 합 ÷ days) */
export interface VisitTimeCell {
    weekday: number;
    hour: number;
    visitors: number;
    pageViews: number;
    /** 더한 칸 수 — 오늘의 지금 시간(집계 중)과 그 뒤 시간은 빼고 센다(0 이면 그 칸은 아직 없음) */
    days: number;
}

/** `GET …/visits/time?from&to` — `analytics_visit_hourly` 를 요일·시로 묶음(168칸, 빈 칸 없이) */
export interface VisitTime {
    range: VisitRange;
    asOf: string;
    cells: VisitTimeCell[];
}

/** 실시간 분당 한 칸 */
export interface VisitMinutePoint {
    /** 그 분의 시작 시각(ISO) */
    at: string;
    pageViews: number;
}

/**
 * `GET …/visits/realtime` — 기간 없음. 지금 접속은 `analytics_visit_active`(방문 수집), 나머지는 **원본 최근 30분**(원본 기록이 꺼져 있으면 빈 배열).
 * 서버가 1분마다 몰아 쓰므로 최대 1분 늦다.
 */
export interface VisitRealtime {
    asOf: string;
    collect: VisitCollect;
    /** 지금 접속(최근 5분) */
    activeNow: number;
    /** 최근 30분 분당 페이지뷰(오래된 차례, 30칸 — 지금 분은 집계 중) */
    perMinute: VisitMinutePoint[];
    /** 최근 5분 많이 본 페이지 10 */
    pages: VisitPageRow[];
    /** 최근 5분 유입 분류(세션 첫 페이지) */
    referrers: { group: VisitReferrerGroup; visits: number }[];
}

/** `GET …/visits/pages/detail?from&to&path` — 페이지 서랍(일별 조회 · 들어온 곳/나간 곳 5) */
export interface VisitPageDetail {
    path: string;
    kind: VisitPageKind;
    title: string | null;
    titleEn: string | null;
    /** 기간 조회수 합 */
    views: number;
    /** 일별 조회(`analytics_page_daily` — 오래된 차례, 빈 날 없이) */
    days: { date: string; views: number }[];
    /** 들어온 곳 상위 5(`analytics_flow_daily` — 이동 수집이 꺼져 있으면 빈 배열) */
    incoming: VisitFlowEnd[];
    incomingTotal: number;
    /** 나간 곳 상위 5 */
    outgoing: VisitFlowEnd[];
    outgoingTotal: number;
}

/** 이동 흐름의 한 쪽 끝 — 들어온 곳 `path` 가 null 이면 「바로 들어옴」(세션 첫 페이지) */
export interface VisitFlowEnd {
    path: string | null;
    title: string | null;
    titleEn: string | null;
    moves: number;
}

/** 상위 이동 한 줄 */
export interface VisitFlowMove {
    from: string;
    to: string;
    moves: number;
}

/**
 * `GET …/visits/flows?from&to&path` — 기준 페이지(`path`, 없으면 서버가 기간에 들어온 이동이 가장 많은 페이지)의 들어온 곳·나간 곳 상위 5 + 기간 전체 상위 이동 10.
 * 같은 주소 다시 보기(A→A)는 서버가 저장하지 않아 없다.
 */
export interface VisitFlows {
    path: string;
    kind: VisitPageKind;
    title: string | null;
    titleEn: string | null;
    /** 들어온 곳 상위 5(「바로 들어옴」 포함) */
    incoming: VisitFlowEnd[];
    /** 들어온 이동 합(상위 5 밖 = 「그 밖」) */
    incomingTotal: number;
    /** 나간 곳 상위 5 */
    outgoing: VisitFlowEnd[];
    /** 나간 이동 합 */
    outgoingTotal: number;
    /** 기간 전체 상위 이동 10 */
    top: VisitFlowMove[];
}
