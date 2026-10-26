import type { AttendanceMonth, AttendResult, MyPointLedgerPage, MyPointLedgerQuery, PointSummary } from '~/composables/point/api/usePointApi.types';

/**
 * 포인트 API — 서버 주소 문자열(`/api/me/points…`·`/api/me/attendance`)이 있는 **유일한 곳**이다. 상태는 없다(요약의 상태는 `usePointSummary`, 출석 달력은 `usePointAttendance`).
 * 계약: `docs/features/point-contract.md`(요약) · `docs/features/member-mypage-contract.md` 「포인트 내역」(내역) · `docs/features/commerce-stage1-contract.md` 「A. 포인트 확장」(적립/충전·소멸·미회수)·「1-3 화면 목업」·「1-3 출석체크」.
 *
 *  - `GET /api/me/points` → `PointSummary`(로그인 필요 401, 본인 것만, `Cache-Control: no-store`) — 커머스 1-1 의 적립/충전·`chargeAvailable`·`expiring`(`date` 는 KST `YYYY-MM-DD`)·`revokeDebt`,
 *    커머스 1-3 의 `today.total`·`rules`(적립 조건·하루 총 한도·본인인증 보너스·연속 출석 보너스)·`attendanceAvailable` 포함.
 *  - `GET /api/me/points/ledger?kind=EARN|USE|REVOKE|CANCEL_USE|EXPIRE&size=1~50&cursor=` → `MyPointLedgerPage`(최신이 위, 로그인 필요 401, `no-store`).
 *    줄의 `restoredUntil`(30일 새 묶음으로 돌려준 경우의 소멸 시각, UTC ISO)·`target`(글·댓글 사유만 — 소멸·사용·상계 줄은 null). 오류: `400 VALIDATION` `fields.kind|size|cursor`.
 *  - `GET /api/me/attendance?month=YYYY-MM` → `AttendanceMonth`(`no-store`). 달 모양이 틀리면 `400 VALIDATION` `fields.month`, 미래 달은 `days: []`.
 *  - `POST /api/me/attendance` → `AttendResult`. `409 ATTENDANCE_ALREADY_DONE`(오늘 이미 출석) · `403 EMAIL_NOT_VERIFIED`·`ACCOUNT_RESTRICTED`(정지) · 사업자 전용 계정은 `403 BUSINESS_ACCOUNT_RESTRICTED`.
 * 포인트 상점 구매·환불은 `usePointShopApi`·`useMyItemsApi` 다.
 */
const PATH = {
    points: '/api/me/points',
    ledger: '/api/me/points/ledger',
    attendance: '/api/me/attendance',
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

        /** 한 달의 출석 — 달을 넘기면 앞 요청을 끊는다(`signal`) */
        attendance: (month: string, signal?: AbortSignal): Promise<AttendanceMonth> => client.get<AttendanceMonth>(PATH.attendance, { query: { month }, signal }),

        /** 오늘 출석 */
        attend: (): Promise<AttendResult> => client.post<AttendResult>(PATH.attendance),
    };
}
