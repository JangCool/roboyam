import type { PointSummary } from '~/composables/point/api/usePointApi.types';

/**
 * 포인트 API — 서버 주소 문자열(`/api/me/points…`)이 있는 **유일한 곳**이다. 상태는 없다(요약의 상태는 `usePointSummary`).
 * 계약: `docs/features/point-contract.md`.
 *
 *  - `GET /api/me/points` → `PointSummary`(로그인 필요 401, 본인 것만, `Cache-Control: no-store`)
 *
 * 셸(헤더 프로필 메뉴·햄버거)이 쓰는 요약(`points`)만 있다 — 내역·출석·교환은 서버에 아직 없다.
 */
const PATH = {
    points: '/api/me/points',
};

export function usePointApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 포인트 요약 — 로그인 필요 */
        points: (): Promise<PointSummary> => client.get<PointSummary>(PATH.points),
    };
}
