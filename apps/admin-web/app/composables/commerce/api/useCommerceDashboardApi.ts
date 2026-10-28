import { mockCommerceDashboard } from '~/composables/commerce/api/mock/commerce-mock';
import type { CommerceDashboardView } from '~/composables/commerce/api/useCommerceDashboardApi.types';

/**
 * 대시보드 커머스 칸 API — 서버 주소 문자열(`/api/admin/commerce/dashboard`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 숫자는 서버가 운영자의 권한 안에서 센다(볼 권한이 없는 칸은 null). commerce.md 19절.
 *  - `GET /api/admin/commerce/dashboard` → `CommerceDashboardView`
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/commerce-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    dashboard: '/api/admin/commerce/dashboard',
};

export function useCommerceDashboardApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH;

    return {
        summary: (): Promise<CommerceDashboardView> =>
            // MOCK:
            mockCommerceDashboard(),
        // REAL: client.get<CommerceDashboardView>(PATH.dashboard),
    };
}
