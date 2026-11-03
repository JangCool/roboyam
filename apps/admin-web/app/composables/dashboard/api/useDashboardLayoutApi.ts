import type { DashboardLayoutBody } from '~/composables/dashboard/api/useDashboardLayoutApi.types';

/**
 * 내 대시보드 구성 API — 서버 주소 문자열(`/api/admin/me/dashboard`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 구성은 운영자마다 따로다(서버는 세션의 운영자로 고른다 — 경로에 ID 가 없다).
 *  - `GET /api/admin/me/dashboard` → `DashboardLayoutBody`(저장한 적 없으면 `gadgets: null`)
 *  - `PUT /api/admin/me/dashboard` `DashboardLayoutBody` → 저장한 `DashboardLayoutBody`(CSRF) · 틀리면 `400 VALIDATION` `fields`(`version` · `columns` · `gadgets` · `gadgets[i].<칸>` · `gadgets[i].config.<칸>`)
 * (서버 `DashboardLayoutController`)
 */
const PATH = {
    me: '/api/admin/me/dashboard',
};

export function useDashboardLayoutApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 구성 */
        get: (): Promise<DashboardLayoutBody> => client.get<DashboardLayoutBody>(PATH.me),

        /** 구성을 저장한다 — 응답은 저장한 구성 */
        put: (body: DashboardLayoutBody): Promise<DashboardLayoutBody> => client.put<DashboardLayoutBody>(PATH.me, body),
    };
}
