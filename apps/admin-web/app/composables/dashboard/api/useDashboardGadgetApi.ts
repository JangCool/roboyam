import type { DashboardGadgetRequest, DashboardGadgetsResponse } from '~/composables/dashboard/api/useDashboardGadgetApi.types';
// MOCK: 방문 통계 3단계 목업(순방문자 한 줄) — 「방문자 통계」 쪽과 같은 숫자를 쓰려고 그 목업을 빌린다(서버가 생기면 지운다)
import VisitAnalyticsMock from '~/composables/analytics/api/mock/visit-analytics-mock';

/**
 * 대시보드 가젯 숫자·목록 API — 서버 주소 문자열(`/api/admin/dashboard/gadgets`)이 있는 **유일한 곳**이다. 상태는 없다.
 *  - `GET /api/admin/dashboard/gadgets?types=A,B,C&limit.A=5&days.B=30` → `DashboardGadgetsResponse`
 *    (종류마다 값 — 권한이 없거나 서버가 아직 줄 수 없거나 수집이 꺼졌으면 null, 둘째는 `unavailable`·셋째는 `disabled` 에도 든다. **한 종류만** 요청했는데 권한이 없으면 403)
 *    방문 분석 종류(`POPULAR_PAGES`·`REFERRERS`)도 같은 묶음으로 보낸다(`docs/features/site-analytics-contract.md` 「API (확정)」).
 * 한 화면의 가젯을 묶어 부르는 일(같은 틱에 그려진 가젯 모으기)은 `useDashboardKindData`(`useDashboardGadgetData.ts`)가 한다 — 여기는 받은 요청 그대로 한 번 부른다.
 * (서버 `DashboardGadgetController`·`DashboardGadgetService`)
 */
const PATH = {
    gadgets: '/api/admin/dashboard/gadgets',
};

export function useDashboardGadgetApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 여러 종류의 숫자·목록을 한 번에 — 종류는 겹치지 않게 넘긴다(종류마다 질의 값이 하나다) */
        load: async (requests: readonly DashboardGadgetRequest[]): Promise<DashboardGadgetsResponse> => {
            const query: Record<string, string | number | undefined> = { types: requests.map((r) => r.type).join(',') };
            for (const r of requests) {
                query[`limit.${r.type}`] = r.query.limit;
                query[`days.${r.type}`] = r.query.days;
            }
            const res = await client.get<DashboardGadgetsResponse>(PATH.gadgets, { query });
            // MOCK: 방문 통계 3단계 — 「방문 현황」의 이번 주·이번 달 순방문자(서버가 `VISITORS` 에 두 칸을 주면 이 두 줄과 목업 import 를 지운다)
            const visitors = res.gadgets.VISITORS;
            if (visitors) res.gadgets.VISITORS = { ...visitors, ...VisitAnalyticsMock.visitorsThisWeekAndMonth() };
            return res;
            // REAL: return client.get<DashboardGadgetsResponse>(PATH.gadgets, { query });
        },
    };
}
