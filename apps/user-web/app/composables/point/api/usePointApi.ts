import { mockPointLedger } from '~/composables/point/api/mock/point-mock';
import type { MyPointLedgerPage, MyPointLedgerQuery, PointSummary } from '~/composables/point/api/usePointApi.types';

/**
 * 포인트 API — 서버 주소 문자열(`/api/me/points…`)이 있는 **유일한 곳**이다. 상태는 없다(요약의 상태는 `usePointSummary`).
 * 계약: `docs/features/point-contract.md`(요약) · `docs/features/member-mypage-contract.md` 「포인트 내역」(내역 — 후보).
 *
 *  - `GET /api/me/points` → `PointSummary`(로그인 필요 401, 본인 것만, `Cache-Control: no-store`) — **서버 연결됨**
 *  - `GET /api/me/points/ledger?kind=EARN|USE|REVOKE&size=1~50&cursor=` → `MyPointLedgerPage` — **서버에 아직 없다(목업)**.
 *    오류: `400 VALIDATION` `fields.kind|size|cursor`.
 *
 * 출석·교환은 서버에 기능이 없어 메서드가 없다.
 * ⚠ MOCK: `ledger` 만 `./mock/point-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    points: '/api/me/points',
    ledger: '/api/me/points/ledger',
};

export function usePointApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 포인트 요약 — 로그인 필요 */
        points: (): Promise<PointSummary> => client.get<PointSummary>(PATH.points),

        /** 내 포인트 내역 한 쪽 — 최신이 위 */
        ledger: (query: MyPointLedgerQuery = {}): Promise<MyPointLedgerPage> =>
            // MOCK:
            mockPointLedger(query),
        // REAL: client.get<MyPointLedgerPage>(PATH.ledger, { query: { ...query } }),
    };
}
