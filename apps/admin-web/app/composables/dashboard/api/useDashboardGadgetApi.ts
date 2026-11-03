import type { DashboardGadgetRequest, DashboardGadgetsResponse } from '~/composables/dashboard/api/useDashboardGadgetApi.types';

/**
 * 대시보드 가젯 숫자·목록 API — 서버 주소 문자열(`/api/admin/dashboard/gadgets`)이 있는 **유일한 곳**이다. 상태는 없다.
 *  - `GET /api/admin/dashboard/gadgets?types=A,B,C&limit.A=5&days.B=30` → `DashboardGadgetsResponse`
 *    (종류마다 값 — 권한이 없거나 서버가 아직 줄 수 없으면 null, 후자는 `unavailable` 에도 든다. **한 종류만** 요청했는데 권한이 없으면 403)
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
        load: (requests: readonly DashboardGadgetRequest[]): Promise<DashboardGadgetsResponse> => {
            const query: Record<string, string | number | undefined> = { types: requests.map((r) => r.type).join(',') };
            for (const r of requests) {
                query[`limit.${r.type}`] = r.query.limit;
                query[`days.${r.type}`] = r.query.days;
            }
            return client.get<DashboardGadgetsResponse>(PATH.gadgets, { query });
        },
    };
}
