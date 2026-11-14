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
    /** 요약·추이 · 페이지 순위·서랍 일별(3단계부터 `analytics_page_daily` 는 이 스위치에 딸린다 — 원본 기록을 꺼도 쌓인다) · 주·월 순방문자 */
    visits: boolean;
    /** 실시간 분당·최근 5분(원본 `analytics_page_views` 최근 30분) */
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

/**
 * 페이지 한 줄 — `title` = 화면 이름(게시판 이름 · 가젯 페이지 이름 …). 서버가 모르면 null.
 * 3단계(결정 ②): 서버가 주소의 동적 자리를 `{id}` 로 묶어 쌓는다(`/posts/{id}` · `/groups/{id}/events/{id}` · `/me/orders/{id}` …, 모르는 주소는 `/{unknown}`) — 이런 줄은 `title`·`titleEn` 이 null 이고
 * 화면이 `UtilVisitPathMessages` 의 표로 「글 보기」 같은 이름을 붙인다(표에 없으면 경로 그대로). 게시판 `/boards/{slug}`·가젯 페이지 `/p/{key}` 는 값을 남긴다.
 */
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

// ════════════════════════════════════════════════════════════════════════════
// 2단계(2026-11-04 — 사용자 결정): 환경 · 검색어 · 봇·크롤러 · 수집 진단 · 정보주체 요청.
// 서버 `VisitAnalyticsAdmin`(admin)의 2단계 레코드와 같은 모양이다. 권한은 모두 `site.read`.
// ════════════════════════════════════════════════════════════════════════════

/** 2단계 수집 스위치 — 각 칸은 「방문 수집 전체(`enabled`) && 그 항목」으로 서버가 접어서 준다(`VisitCollect` 와 같은 규칙) */
export interface VisitCollectMore {
    enabled: boolean;
    /** 환경(브라우저·OS·화면 너비 분류값 — 세션 첫 페이지 신호의 `env`) */
    environment: boolean;
    /** 우리 사이트 검색어(서버가 `GET /api/search` 요청에서 센다) */
    searchTerms: boolean;
    /** 봇·크롤러 요청 수(서버가 요청의 UA 로 센다) */
    bots: boolean;
    /** 방문자·페이지뷰 — 봇 쪽의 「사람 대비」 분모(사람 페이지뷰)를 쓸 수 있나 */
    visits: boolean;
}

/** 기기 — 서버가 신호 요청의 UA(Mobile·Tablet 표시)로 가른다(원본 UA 는 저장하지 않는다) */
export const VISIT_DEVICES = ['MOBILE', 'TABLET', 'DESKTOP'] as const;
export type VisitDevice = (typeof VISIT_DEVICES)[number];

/** 브라우저 분류값 — user-web `@irobit/analytics` 의 `util-visit-env.ts` 와 같은 값(그 밖은 OTHER) */
export const VISIT_BROWSERS = ['CHROME', 'SAFARI', 'EDGE', 'SAMSUNG', 'WHALE', 'FIREFOX', 'OTHER'] as const;
export type VisitBrowser = (typeof VISIT_BROWSERS)[number];

/** OS 분류값 — 같은 파일과 같은 값 */
export const VISIT_OSES = ['WINDOWS', 'MACOS', 'ANDROID', 'IOS', 'LINUX', 'OTHER'] as const;
export type VisitOs = (typeof VISIT_OSES)[number];

/** 화면 너비 구간(`window.innerWidth`) — ~480 · ~768 · ~1024 · ~1440 · 1440 넘음. 같은 파일과 같은 값 */
export const VISIT_SCREENS = ['W480', 'W768', 'W1024', 'W1440', 'WIDE'] as const;
export type VisitScreen = (typeof VISIT_SCREENS)[number];

/** 분류 한 줄 */
export interface VisitShareRow<K extends string> {
    key: K;
    /** 방문(세션 첫 페이지) 수 */
    visits: number;
}

/** 일별 기기 한 칸(오래된 차례, 빈 날 없이 — 없는 기기는 0) */
export interface VisitDeviceSeriesPoint {
    date: string;
    devices: Record<VisitDevice, number>;
}

/**
 * `GET /api/admin/analytics/visits/environment?from&to` — **방문 기준**(세션 첫 페이지 하나 = 1)으로 센다.
 * 원천: `analytics_env_daily(day, device, browser, os, screen, visits)` 하나 — 네 분류를 한 줄에 두고 기간으로 묶어 분류별 합을 낸다(하루 줄 수 ≤ 3×7×6×5 = 630).
 */
export interface VisitEnvironment {
    range: VisitRange;
    asOf: string;
    collect: VisitCollectMore;
    /** 분류한 방문 합(분모) */
    total: number;
    devices: VisitShareRow<VisitDevice>[];
    browsers: VisitShareRow<VisitBrowser>[];
    oses: VisitShareRow<VisitOs>[];
    screens: VisitShareRow<VisitScreen>[];
    series: VisitDeviceSeriesPoint[];
}

/** 검색어 한 줄 */
export interface VisitSearchTerm {
    /** 서버가 다듬은 검색어(앞뒤 공백·연속 공백 정리, 영문 소문자) */
    term: string;
    searches: number;
    /** 결과가 0건이었던 검색 수 */
    noResult: number;
}

/** 결과 없는 검색어 한 줄 */
export interface VisitNoResultTerm {
    term: string;
    /** 결과 없이 끝난 검색 수 */
    searches: number;
    /** 마지막으로 결과 없이 검색된 날(한국 날짜) */
    lastDay: string;
}

/**
 * `GET /api/admin/analytics/visits/search?from&to` — 우리 사이트 검색(`/search`)의 검색어. 외부 검색 엔진 검색어는 요즘 넘어오지 않아 없다.
 * 원천: `analytics_search_daily(day, term, searches, no_result)` + `analytics_search_filtered_daily` — 서버가 `GET /api/search` 성공 응답 중 첫 쪽(`page == 1`)만 메모리에 세다가 1분마다 더하기 upsert.
 * 저장하지 않는 검색어(`totals.filtered` 로만 센다): 전화번호·이메일·주민등록번호 모양 · 2글자 미만 · 50자 초과.
 */
export interface VisitSearch {
    range: VisitRange;
    asOf: string;
    collect: VisitCollectMore;
    totals: {
        /** 검색 수(걸러진 것 제외) */
        searches: number;
        /** 결과 없는 검색 수 */
        noResult: number;
        /** 서로 다른 검색어 수 */
        terms: number;
        /** 개인정보 모양·길이로 저장하지 않은 검색 수 */
        filtered: number;
    };
    /** 검색 수 상위 20 */
    terms: VisitSearchTerm[];
    /** 결과 없는 검색 수 상위 20 */
    noResultTerms: VisitNoResultTerm[];
}

/** 봇 종류 — 서버가 UA 로 가른다(광고 노출과 같은 봇 정규식 + 종류 이름) */
export const VISIT_BOT_KINDS = ['GOOGLE', 'NAVER', 'BING', 'DAUM', 'OTHER_CRAWLER', 'TOOL'] as const;
export type VisitBotKind = (typeof VISIT_BOT_KINDS)[number];

/** 봇 요청 · 사람 페이지뷰 */
export interface VisitBotCounts {
    bots: number;
    /** 사람 페이지뷰(`analytics_visit_hourly`) — 방문자·페이지뷰 수집이 꺼져 있으면 null(비율을 내지 않는다) */
    humans: number | null;
}

/** 일별 봇 한 칸(오래된 차례, 빈 날 없이) */
export interface VisitBotSeriesPoint {
    date: string;
    kinds: Record<VisitBotKind, number>;
}

/**
 * `GET /api/admin/analytics/visits/bots?from&to` — 봇·크롤러 요청 수(봇은 방문 통계에서 빠져 있다).
 * 원천: `analytics_bot_daily(day, kind, requests)` — 공개 화면 HTML(api `SitePageHandler` 가 `index.html` 을 내릴 때) 요청 하나를 1로 세다가 1분마다 더하기 upsert(`/api/**` 는 세지 않는다).
 */
export interface VisitBots {
    range: VisitRange;
    asOf: string;
    collect: VisitCollectMore;
    /** 오늘(기간과 무관) */
    today: VisitBotCounts;
    /** 기간 합 */
    totals: VisitBotCounts;
    kinds: { kind: VisitBotKind; requests: number }[];
    series: VisitBotSeriesPoint[];
}

/** 수집 상태 — 서버가 판정한다(정상 · 지연 · 멈춤 · 꺼짐) */
export type VisitHealthStatus = 'OK' | 'DELAYED' | 'STOPPED' | 'OFF';

/**
 * 문제 코드 — 화면은 코드로 문장을 고른다(숫자·이름은 칸에서).
 *  - `INSTANCE_SILENT` 서버(`instance`)가 `since` 뒤로 기록하지 않음 · `FLUSH_FAILED` 지난 24시간 1분 기록 실패 `count` 번
 *  - `BUFFER_DROPPED` 버퍼가 넘쳐 신호 `count` 개를 버림 · `CRON_FAILED` 크론(`job`) 마지막 실행 실패(`since` = 그 실행 시각) · `CRON_LATE` 크론(`job`)이 `since` 뒤로 돌지 않음
 *  - `RAW_BACKLOG` 보관 기간이 지난 원본이 `count` 일치 남음 · `NEXT_PARTITION_MISSING` 다음 달 원본 조각이 없음 · `COLLECT_OFF` 방문 수집이 꺼짐
 */
export const VISIT_HEALTH_ISSUE_CODES = ['INSTANCE_SILENT', 'FLUSH_FAILED', 'BUFFER_DROPPED', 'CRON_FAILED', 'CRON_LATE', 'RAW_BACKLOG', 'NEXT_PARTITION_MISSING', 'COLLECT_OFF'] as const;
export type VisitHealthIssueCode = (typeof VISIT_HEALTH_ISSUE_CODES)[number];

/** 문제 한 줄(심각한 차례) */
export interface VisitHealthIssue {
    code: VisitHealthIssueCode;
    /** 빨강(멈춤·실패) · 노랑(지연·버림) */
    level: 'ERROR' | 'WARN';
    instance: string | null;
    job: string | null;
    count: number | null;
    since: string | null;
}

/** 서버(인스턴스) 한 줄 — 최근 24시간 */
export interface VisitHealthInstance {
    /** 서버 이름(호스트 이름·작업 ID) */
    instance: string;
    /** 마지막 1분 기록 시각(신호가 없어도 1분마다 「살아 있음」을 적는다) — 없으면 null */
    lastWriteAt: string | null;
    flushOk: number;
    flushFail: number;
    /** 버퍼가 넘쳐 버린 신호 */
    dropped: number;
    /** 무시한 신호 — 봇 UA */
    ignoredBot: number;
    /** 무시한 신호 — 형식 오류 */
    ignoredInvalid: number;
    /** 뺀 신호 — 운영자 회원 계정·AI 회원이 로그인해 보낸 신호(3단계 — 방문 통계 전체에서 뺀다) */
    ignoredExcluded: number;
    /** 「알 수 없는 주소」(`/{unknown}`)로 묶은 신호 — 라우트 표에 없거나 없는 게시판·가젯 페이지(버리지 않고 그 한 줄로 센다, 3단계) */
    unknownPath: number;
}

/** 최근 기록 실패 한 줄 */
export interface VisitHealthFailure {
    at: string;
    instance: string;
    /** 사유 코드(`DB_TIMEOUT` · `DB_UNAVAILABLE` · `CONSTRAINT` · `UNKNOWN`) */
    reason: string;
    /** 서버가 남긴 짧은 설명 — 「예외 이름 (SQLSTATE x) — 묶음 이름」(개인 정보 없음) */
    detail: string | null;
}

/** 크론 결과 — 배치 로그와 같은 값 */
export type VisitCronStatus = 'RUNNING' | 'OK' | 'FAIL' | 'SKIP';

/** 크론 한 줄 — 배치 로그(`batch_runs`)의 마지막 실행 */
export interface VisitHealthCron {
    /** 배치 키(`analytics-page-view-partitions` …) — 배치 로그 화면 `?jobName=` */
    job: string;
    /** 실행 주기 — 배치 정의의 cron 식(배치 관리 화면과 같은 값, 한국 시각 — `*\/10 * * * *` · `25 0 * * *`). 화면은 `UtilBatchMessages.scheduleText` 로 읽는다 */
    cron: string | null;
    lastRunAt: string | null;
    /** 돈 적 없으면 null */
    lastStatus: VisitCronStatus | null;
    processed: number | null;
    /** 실패·건너뜀 사유 */
    message: string | null;
    /** 예정보다 늦음(서버 판정 — 주기의 2배를 넘김) */
    late: boolean;
}

/** 원본 보관 현황 */
export interface VisitHealthRaw {
    /** 원본 기록이 켜져 있나 */
    rawEvents: boolean;
    /** 보관 개월 수(이번 달 포함 — 2 = 이번 달 + 지난달, 3단계 결정 ①) */
    retentionMonths: number;
    /** 가장 오래된 원본의 날(한국 날짜) — 없으면 null */
    oldestDay: string | null;
    /** 월 조각 수 */
    partitions: number;
    /** 대략 줄 수(`pg_class.reltuples` 합 — 정확하지 않다) */
    approxRows: number | null;
    /** 다음 달 조각이 만들어져 있나 */
    nextPartitionReady: boolean;
}

/**
 * `GET /api/admin/analytics/visits/health` — 기간 없음(최근 24시간). 운영 점검용.
 * 원천: `analytics_collector_beats(instance, last_write_at)`(1분마다 한 줄 upsert) · `analytics_collector_hourly(hour, instance, flush_ok, flush_fail, dropped, ignored_bot, ignored_invalid)` ·
 * `analytics_collector_failures(at, instance, reason, detail)`(7일 보관) · `batch_runs`(배치 로그) · `pg_inherits`/`pg_class`(조각).
 */
export interface VisitHealth {
    asOf: string;
    status: VisitHealthStatus;
    /** 문제(심각한 차례) — 비면 「문제 없음」 */
    issues: VisitHealthIssue[];
    instances: VisitHealthInstance[];
    /** 최근 실패 5 */
    failures: VisitHealthFailure[];
    crons: VisitHealthCron[];
    raw: VisitHealthRaw;
}

/** 정보주체 요청 상태 — 대기(DB 작업이 아직 돌지 않음) · 완료 */
export type VisitForgetStatus = 'PENDING' | 'DONE';

/** 방문 기록 삭제 요청 한 줄 — 방문자 ID 해시로만 처리하므로 회원·방문자 ID 는 보이지 않는다 */
export interface VisitForgetRequest {
    /** 요청 번호 — 서버 Snowflake 숫자를 글자로(정밀도 때문에 number 로 바꾸지 않는다) */
    id: string;
    receivedAt: string;
    status: VisitForgetStatus;
    /** 지운 줄 수(원본 + 「그 날 셌다」 표시 + 지금 접속) — 대기면 null */
    deletedRows: number | null;
    processedAt: string | null;
}

/**
 * `GET /api/admin/analytics/visits/requests?page&size` — 최신 차례. 원천: `analytics_forget_requests(id, visitor_hash, received_at, status, deleted_rows, processed_at)`.
 * 처리는 DB 크론(`analytics-forget-requests`, 5분마다) — 운영자가 할 일은 없다.
 */
export interface VisitForgetPage {
    items: VisitForgetRequest[];
    total: number;
    /** 지금 대기 중인 요청 수 */
    pending: number;
    /** 지난 30일 요청 수 */
    last30Days: number;
}

// ════════════════════════════════════════════════════════════════════════════
// 3단계(2026-11-11 설계 합의 — 계약 「3단계」 결정 ④ 화면 확정): 주·월 순방문자(WAU·MAU) · 월별 보기. 아래 모양이 응답의 원천이다(서버가 그대로 따른다).
// 권한은 모두 `site.read`. 수집 스위치는 「방문자·페이지뷰」(`visits`)에 딸린다(새 칸 없음).
// ════════════════════════════════════════════════════════════════════════════

/** 순방문자를 세는 대상 — 방문자(브라우저의 방문자 ID) · 회원(로그인한 회원 번호) */
export const VISIT_AUDIENCES = ['VISITOR', 'MEMBER'] as const;
export type VisitAudience = (typeof VISIT_AUDIENCES)[number];

/**
 * 순방문자 한 기간 — 달력 기준(주는 월요일 ~ 일요일, 달은 1일 ~ 말일, 한국 날짜, 양끝 포함).
 * `count` = 그 기간에 한 번 이상 온 사람 수(중복 없이, 정확값 — 서버 숫자 표의 그 기간 줄 합). 세기 시작 전 기간이면 null(「집계 전」).
 * `partial` = 아직 끝나지 않은 기간(이번 주·이번 달 — 「진행 중」).
 */
export interface VisitUniquePeriod {
    /** 기간 첫날 `YYYY-MM-DD` */
    start: string;
    /** 기간 끝날 `YYYY-MM-DD`(진행 중이어도 기간의 끝 — 오늘이 아니다) */
    end: string;
    count: number | null;
    partial: boolean;
}

/** 이번 기간 · 지난 기간 · 「지난 기간의 같은 시점까지」 */
export interface VisitUniqueCompare {
    current: VisitUniquePeriod;
    previous: VisitUniquePeriod;
    /**
     * 지난 기간을 이번 기간과 같은 시점까지 자른 순방문자(이번 주가 수요일까지면 지난 주 월~수) — 증감 칩의 비교값.
     * 서버 숫자 표의 「그 날짜까지의 합」(계약 결정 ④). 지난 기간이 집계 전이면 null.
     */
    previousSamePoint: number | null;
}

/** 대상 하나(방문자 또는 회원)의 순방문자 */
export interface VisitUniquesAudience {
    week: VisitUniqueCompare;
    month: VisitUniqueCompare;
    /** 최근 12주(오래된 → 최근, 마지막이 이번 주) */
    weeks: VisitUniquePeriod[];
    /** 최근 12개월(오래된 → 최근, 마지막이 이번 달) */
    months: VisitUniquePeriod[];
}

/**
 * `GET /api/admin/analytics/visits/uniques` — 기간 쿼리 없음(늘 오늘 기준 — 화면에서 고른 기간과 무관). 「방문자 통계 › 개요」 순방문자 카드 · 대시보드 「방문 현황」 한 줄.
 * 원천(계약 결정 ④): 숫자 표 `(대상, 종류 WEEK|MONTH, 기간 시작, 날짜, 새로 센 수)` — 기간 순방문자 = 그 기간 줄 합, 같은 시점까지 = 그 날짜까지의 합.
 * 방문자는 브라우저 기준 · 회원은 로그인한 회원 기준 · 봇과 운영자 회원 계정·AI 회원은 빠진다. 수집이 꺼져 있으면(`collect.visits` false) 화면은 「수집이 꺼져 있어요」.
 */
export interface VisitUniques {
    /** 센 시각(ISO — 서버가 1분마다 몰아 쓰므로 최대 1분 늦다) */
    asOf: string;
    collect: VisitCollect;
    audiences: Record<VisitAudience, VisitUniquesAudience>;
}

/** 월별 보기의 기간 — 달 `YYYY-MM`, 양끝 포함(최대 36개월) */
export interface VisitMonthRange {
    from: string;
    to: string;
}

/**
 * 월별 한 칸 — 빈 달 없이 오래된 차례(수집 전 달은 0).
 * `visitorsDailySum` = **날마다 센 방문자의 그 달 합**(같은 사람이 이틀 오면 2 — 월 순방문자가 아니다, 계약 「월별 표의 방문자」). 월 순방문자는 `uniqueVisitors`(방문자)·`uniqueMembers`(회원) — 결정 ④ 숫자 표(MONTH)의 그 달 줄 합.
 */
export interface VisitMonthlyPoint {
    /** `YYYY-MM` */
    month: string;
    visitorsDailySum: number;
    newVisitors: number;
    pageViews: number;
    /** 그 달 순방문자(MAU, 방문자 — 브라우저 기준, 중복 없이). 세기 시작 전 달이면 null */
    uniqueVisitors: number | null;
    /** 그 달 회원 순방문자(MAU, 회원 — 로그인한 회원 번호 기준, 중복 없이 · 운영자·AI 회원 제외). 세기 시작 전 달이면 null(결정 ④ 화면 확정 ⓑ) */
    uniqueMembers: number | null;
    /** 진행 중인 달(이번 달 — 「집계 중」) */
    partial: boolean;
}

/**
 * `GET /api/admin/analytics/visits/monthly?from=YYYY-MM&to=YYYY-MM` — 일별이 13개월만 남으므로(결정 ③) 긴 기간은 월별 표에서 읽는다(이번 달은 서버가 일별을 더해 채운다).
 * 기간 오류 400 VALIDATION(일별 기간과 같은 규칙): 비었거나·형식·`from>to`·36개월 초과 = `fields.from`, 이번 달 뒤 = `fields.to`.
 * 원천: 월 방문 표(방문자 일별 합·신규·페이지뷰) · 월 페이지 표 · 월 유입 표 · 순방문자 숫자 표(MONTH, 방문자·회원). 아직 합치지 않은 달(이번 달 · 1~2일의 지난달)은 일별 표를 더해 채운다.
 * 수집 스위치(결정 ④ 화면 확정 ⓐ): 꺼져 있어도 **쌓인 지난 기록은 그대로 준다**(0 으로 가리지 않는다) — 화면이 그 위에 「지금은 수집이 꺼져 있어 이번 달은 늘지 않아요」를 얹는다.
 * 쌓인 게 하나도 없고 꺼져 있으면 화면은 「수집이 꺼져 있어요」.
 */
export interface VisitMonthly {
    range: VisitMonthRange;
    asOf: string;
    /** 지금 스위치 값(가림 판단이 아니라 안내용 — 위 설명) */
    collect: VisitCollect;
    /** 기간 합(방문자는 일별 합) */
    totals: { visitorsDailySum: number; newVisitors: number; pageViews: number };
    months: VisitMonthlyPoint[];
    /** 기간 많이 본 페이지 상위 10(묶음 주소 그대로 — 페이지 순위와 같은 줄 모양) */
    pages: VisitPageRow[];
    /** 기간 전체 조회수(비율 막대의 분모) */
    pagesTotalViews: number;
    /** 기간 유입 분류(세션 첫 페이지 수 — 유입 경로 수집이 지금 꺼져 있어도 쌓인 기록은 준다) */
    referrers: { total: number; groups: { group: VisitReferrerGroup; visits: number }[] };
}
