import type {
    VisitBots,
    VisitEnvironment,
    VisitFlows,
    VisitForgetPage,
    VisitHealth,
    VisitMonthly,
    VisitMonthRange,
    VisitPageDetail,
    VisitPagePage,
    VisitPageQuery,
    VisitRange,
    VisitRealtime,
    VisitReferrerQuery,
    VisitReferrers,
    VisitSearch,
    VisitSummary,
    VisitTime,
    VisitUniques,
} from '~/composables/analytics/api/useVisitAnalyticsApi.types';
// MOCK: 3단계 목업 — 아래 두 줄은 MOCK 줄만 쓴다(서버가 생기면 함께 지운다)
import VisitAnalyticsMock from '~/composables/analytics/api/mock/visit-analytics-mock';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';

/**
 * 방문 분석 API(관리자, 「방문자 통계」 묶음 6쪽) — 서버 주소(`/api/admin/analytics/visits/**`)가 있는 **유일한 곳**이다. 상태는 없다. 모두 GET.
 * 권한: 모두 `site.read`. 계약 `docs/features/site-analytics-contract.md`. 기간은 한국 날짜 `from`·`to`(양끝 포함, 최대 90일 — 넘으면 400 VALIDATION `fields.from`).
 *   - `GET …/visits/summary?from&to`  → `VisitSummary`(합계 · 직전 기간 · 추이 · 수집 스위치 · 지금 접속)  — `analytics_visit_hourly` + `analytics_visit_active`
 *   - `GET …/visits/pages?from&to&kind&q&page&size` → `VisitPagePage`  — `analytics_page_daily`
 *   - `GET …/visits/pages/detail?from&to&path` → `VisitPageDetail`(페이지 서랍)  — `analytics_page_daily`(한 경로) + `analytics_flow_daily`(그 경로의 들어온·나간 5)
 *   - `GET …/visits/referrers?from&to&detail` → `VisitReferrers`  — `analytics_referrer_daily` + 일별 캠페인 집계 표(`detail=false` 면 일별 추이·캠페인을 읽지 않는다)
 *   - `GET …/visits/flows?from&to&path` → `VisitFlows`  — `analytics_flow_daily`
 *   - `GET …/visits/time?from&to` → `VisitTime`(요일 × 시 168칸)  — `analytics_visit_hourly`
 *   - `GET …/visits/realtime` → `VisitRealtime`  — `analytics_visit_active` + 원본 최근 30분
 * 2단계(계약 「2단계」 — 각 응답의 `collect` 는 「방문 수집 전체 && 그 항목」으로 접은 값):
 *   - `GET …/visits/environment?from&to` → `VisitEnvironment`(기기·브라우저·OS·화면 너비 + 일별 기기)  — `analytics_env_daily`
 *   - `GET …/visits/search?from&to` → `VisitSearch`(검색어 순위 · 결과 없는 검색어)  — `analytics_search_daily` + `analytics_search_filtered_daily`
 *   - `GET …/visits/bots?from&to` → `VisitBots`(오늘·기간 · 종류별 · 일별)  — `analytics_bot_daily` + 사람 페이지뷰 `analytics_visit_hourly`
 *   - `GET …/visits/health` → `VisitHealth`(최근 24시간 — 서버별 기록·실패·크론·원본 보관)  — `analytics_collector_*` + 배치 로그
 *   - `GET …/visits/requests?page&size` → `VisitForgetPage`(정보주체 방문 기록 삭제 요청)  — `analytics_forget_requests`
 * 3단계(계약 「3단계」 — ⚠ 목업, 서버 없음. 모양은 `.types.ts` 의 후보):
 *   - `GET …/visits/uniques` → `VisitUniques`(주·월 순방문자 — 방문자·회원, 기간 쿼리 없음)  — 순방문자 숫자 표
 *   - `GET …/visits/monthly?from=YYYY-MM&to=YYYY-MM` → `VisitMonthly`(월별 보기 — 최대 36개월)  — 월별 집계 표
 * 오류는 모두 `ApiError`(useApiClient)로 던져진다.
 */
const PATH = {
    summary: '/api/admin/analytics/visits/summary',
    pages: '/api/admin/analytics/visits/pages',
    pageDetail: '/api/admin/analytics/visits/pages/detail',
    referrers: '/api/admin/analytics/visits/referrers',
    flows: '/api/admin/analytics/visits/flows',
    time: '/api/admin/analytics/visits/time',
    realtime: '/api/admin/analytics/visits/realtime',
    environment: '/api/admin/analytics/visits/environment',
    search: '/api/admin/analytics/visits/search',
    bots: '/api/admin/analytics/visits/bots',
    health: '/api/admin/analytics/visits/health',
    requests: '/api/admin/analytics/visits/requests',
    uniques: '/api/admin/analytics/visits/uniques',
    monthly: '/api/admin/analytics/visits/monthly',
};

export function useVisitAnalyticsApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 요약·추이 */
        summary: (range: VisitRange): Promise<VisitSummary> => client.get<VisitSummary>(PATH.summary, { query: { ...range } }),

        /** 페이지 순위 한 쪽(`page` 0부터) */
        pages: (query: VisitPageQuery): Promise<VisitPagePage> => client.get<VisitPagePage>(PATH.pages, { query: { ...query } }),

        /** 페이지 한 개의 일별 조회·들어온 곳·나간 곳(서랍) */
        pageDetail: (query: VisitRange & { path: string }): Promise<VisitPageDetail> => client.get<VisitPageDetail>(PATH.pageDetail, { query: { ...query } }),

        /** 유입 경로 */
        referrers: (query: VisitReferrerQuery): Promise<VisitReferrers> => client.get<VisitReferrers>(PATH.referrers, { query: { ...query } }),

        /** 이동 흐름 — `path` 가 없으면 서버가 기준 페이지를 고른다 */
        flows: (query: VisitRange & { path?: string }): Promise<VisitFlows> => client.get<VisitFlows>(PATH.flows, { query: { ...query } }),

        /** 요일·시간대 */
        time: (range: VisitRange): Promise<VisitTime> => client.get<VisitTime>(PATH.time, { query: { ...range } }),

        /** 실시간 */
        realtime: (): Promise<VisitRealtime> => client.get<VisitRealtime>(PATH.realtime),

        /** 환경(기기·브라우저·OS·화면 너비) */
        environment: (range: VisitRange): Promise<VisitEnvironment> => client.get<VisitEnvironment>(PATH.environment, { query: { ...range } }),

        /** 우리 사이트 검색어 */
        search: (range: VisitRange): Promise<VisitSearch> => client.get<VisitSearch>(PATH.search, { query: { ...range } }),

        /** 봇·크롤러 */
        bots: (range: VisitRange): Promise<VisitBots> => client.get<VisitBots>(PATH.bots, { query: { ...range } }),

        /** 수집 진단(최근 24시간 — 기간 없음) */
        health: (): Promise<VisitHealth> => client.get<VisitHealth>(PATH.health),

        /** 정보주체 요청 한 쪽(`page` 0부터, 최신 차례) */
        requests: (params: { page: number; size: number }): Promise<VisitForgetPage> => client.get<VisitForgetPage>(PATH.requests, { query: { ...params } }),

        /*
         * 3단계 — ⚠ 목업(화면 확정 전, 2026-11-11). 숫자는 `mock/visit-analytics-mock.ts` 가 만들고, 수집 스위치만 실제 서버의 오늘 요약(`collect`)에서 가져온다.
         * REAL 줄은 후보 주소다(계약 「3단계」 미구현).
         */

        /** 주·월 순방문자(방문자·회원) — 고른 기간과 무관하게 늘 오늘 기준 */
        async uniques(): Promise<VisitUniques> {
            // MOCK:
            const today = UtilVisitAnalytics.today();
            const s = await client.get<VisitSummary>(PATH.summary, { query: { from: today, to: today } });
            return VisitAnalyticsMock.uniques(s.collect);
            // REAL: return client.get<VisitUniques>(PATH.uniques);
        },

        /** 월별 보기(달 `YYYY-MM` 양끝 포함, 최대 36개월) */
        async monthly(range: VisitMonthRange): Promise<VisitMonthly> {
            // MOCK:
            const today = UtilVisitAnalytics.today();
            const s = await client.get<VisitSummary>(PATH.summary, { query: { from: today, to: today } });
            return VisitAnalyticsMock.monthly(range, s.collect);
            // REAL: return client.get<VisitMonthly>(PATH.monthly, { query: { ...range } });
        },
    };
}
