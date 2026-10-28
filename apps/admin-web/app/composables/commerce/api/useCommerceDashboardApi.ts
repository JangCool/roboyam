import type { CommerceDashboardView } from '~/composables/commerce/api/useCommerceDashboardApi.types';

/**
 * 대시보드 커머스 칸 API — 서버 주소 문자열(`/api/admin/commerce/dashboard`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 숫자는 서버가 운영자의 권한 안에서 센다(볼 권한이 없는 칸은 null). commerce.md 19절.
 *  - `GET /api/admin/commerce/dashboard` → `CommerceDashboardView`
 *
 */
const PATH = {
    dashboard: '/api/admin/commerce/dashboard',
};

export function useCommerceDashboardApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        summary: (): Promise<CommerceDashboardView> =>
            client.get<CommerceDashboardView>(PATH.dashboard),
    };
}
