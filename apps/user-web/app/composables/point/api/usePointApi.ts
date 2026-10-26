import type { MyPointLedgerPage, MyPointLedgerQuery, PointSummary } from '~/composables/point/api/usePointApi.types';

/**
 * 포인트 API — 서버 주소 문자열(`/api/me/points…`)이 있는 **유일한 곳**이다. 상태는 없다(요약의 상태는 `usePointSummary`).
 * 계약: `docs/features/point-contract.md`(요약) · `docs/features/member-mypage-contract.md` 「포인트 내역」(내역) · `docs/features/commerce-stage1-contract.md` 「A. 포인트 확장」(적립/충전·소멸·미회수).
 *
 *  - `GET /api/me/points` → `PointSummary`(로그인 필요 401, 본인 것만, `Cache-Control: no-store`) — 커머스 1-1 의 적립/충전·`chargeAvailable`·`expiring`(`date` 는 KST `YYYY-MM-DD`)·`revokeDebt` 포함.
 *  - `GET /api/me/points/ledger?kind=EARN|USE|REVOKE|CANCEL_USE|EXPIRE&size=1~50&cursor=` → `MyPointLedgerPage`(최신이 위, 로그인 필요 401, `no-store`).
 *    줄의 `restoredUntil`(30일 새 묶음으로 돌려준 경우의 소멸 시각, UTC ISO)·`target`(글·댓글 사유만 — 소멸·사용·상계 줄은 null). 오류: `400 VALIDATION` `fields.kind|size|cursor`.
 * 출석은 서버에 기능이 없어 메서드가 없다. 포인트 상점 구매·환불은 `usePointShopApi`·`useMyItemsApi` 다.
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
        // 빈 값(undefined)은 `useApiClient` 가 질의 문자열에서 뺀다 — 거르기 「전체」는 `kind` 를 보내지 않는다
        ledger: (query: MyPointLedgerQuery = {}): Promise<MyPointLedgerPage> => client.get<MyPointLedgerPage>(PATH.ledger, { query: { kind: query.kind, size: query.size, cursor: query.cursor } }),
    };
}
