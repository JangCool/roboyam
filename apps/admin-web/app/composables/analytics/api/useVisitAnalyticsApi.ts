import type {
    VisitFlows,
    VisitPageDetail,
    VisitPagePage,
    VisitPageQuery,
    VisitRange,
    VisitRealtime,
    VisitReferrerQuery,
    VisitReferrers,
    VisitSummary,
    VisitTime,
} from '~/composables/analytics/api/useVisitAnalyticsApi.types';

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
    };
}
