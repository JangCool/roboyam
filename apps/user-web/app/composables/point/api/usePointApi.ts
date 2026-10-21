import { mockPoints } from '~/composables/point/api/mock/point-mock';
import type { PointSummary } from '~/composables/point/api/usePointApi.types';

/**
 * 포인트·출석·교환 API — 서버 주소 문자열(`/api/me/points/…`)이 있는 **유일한 곳**이다 (frontend-guide 2장). 상태는 없다(요약의 상태는 `usePointSummary`).
 * 규약: community `docs/api-point.md`.
 *
 * ⚠ 단순 버전 — 원본(community)은 내역·출석·교환 등 포인트 화면 전체의 메서드를 가진다. 셸(헤더 프로필 메뉴·햄버거)이 쓰는 요약(`points`)만 남겼다.
 * ⚠ MOCK: 지금은 서버가 없어서 `./mock/point-mock.ts` 의 목업을 부른다. 서버가 붙으면 `// MOCK:` 줄을 `// REAL:` 줄로 바꾼다.
 */
const PATH = {
    points: '/api/me/points',
};

export function usePointApi() {
    // REAL: const client = useApiClient({ refreshCsrf: () => useAuthApi().me() });
    void PATH;

    return {
        /** 내 포인트 요약 — 로그인 필요 */
        points: (): Promise<PointSummary> =>
            // MOCK:
            mockPoints(),
        // REAL: client.get<PointSummary>(PATH.points),
    };
}
