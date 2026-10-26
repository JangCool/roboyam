import type {
    PointAdjustRequest,
    PointAdjustResponse,
    PointLedgerPage,
    PointLedgerQuery,
    PointMemberCandidate,
    PointMemberLots,
    PointMemberLotsQuery,
    PointSettingsResponse,
    PointSettingsValues,
} from '~/composables/point/api/usePointApi.types';

/**
 * 포인트 API — 서버 주소 문자열(`/api/admin/points…`·`/api/admin/settings/points`·`/api/admin/members/{id}/points`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 서버 `PointAdminController`. 계약: `docs/features/point-contract.md`(community `docs/api-point.md` 「관리자 API」 + 전체 원장 조회·조정 대상 후보 조회).
 *
 * 오류는 모두 `ApiError`(useApiClient)로 던져진다. 쓰기는 CSRF. 권한: 조회 `point.read`, 정책 변경 `point.settings`, 수동 지급·차감 `point.adjust`(문턱 초과는 + `point.adjust.large`). 응답은 `Cache-Control: no-store`.
 *   - `GET  /api/admin/points/ledger?from&to&kind&reasons&q&page&size` → 200 `PointLedgerPage` `{ items, total, summary: { earned, spent, adjustCount } }` — 줄마다 `freeDelta`·`paidDelta`
 *       (최신이 위. `from`·`to` 는 순간(UTC ISO), `reasons` 는 반복 이름, `q` 는 닉네임 부분·회원번호 같음·이메일(`member.pii` 있으면 부분, 없으면 전체 주소가 같을 때만), `page` 0부터, `size` 1~100.
 *        `summary` 는 같은 조건의 전체 줄 기준. 탈퇴 회원의 줄은 나오지 않는다. 틀린 조건은 칸을 모아 400 `VALIDATION` + `fields.from|to|kind|reasons|page|size`(시작 > 끝은 `fields.from`). `point.read`)
 *   - `GET  /api/admin/points/member-candidates?q=` → 200 `PointMemberCandidate[]` (탈퇴하지 않은 회원(정상·정지) 최대 20명, 최근 가입순, 지금 잔액(합계·적립·충전) 포함. 이메일은 `member.pii` 가 없으면 가린다. `point.adjust`)
 *   - `POST /api/admin/members/{id}/points` `{ delta, reason }` → 200 `{ applied, balance }`
 *       (적립(무상) 포인트만. delta 0 아닌 정수 −100000~100000 · reason 정규화 뒤 1~100자(회원 내역에 공개). 차감은 적립 0P 까지라 `applied` 가 요청보다 작을 수 있고, 0 이면 원장 줄·알림이 없다.
 *        |delta| 가 문턱(정책 `adjustLargeThreshold`, 기본 10000, 0 = 모든 조정)을 넘으면 `point.adjust.large` 가 필요하다 — 없으면 403 `POINT_ADJUST_LARGE_REQUIRED`(서버 문구에 문턱).
 *        차감이 반영되면 회원에게 알림 `POINTS_REVOKED`. 감사 기록 `POINT_ADJUST`(고액이면 `large: true`). 400 `VALIDATION` `fields.delta|reason` · 404 `MEMBER_NOT_FOUND`(탈퇴 포함). `point.adjust`)
 *   - `GET  /api/admin/members/{id}/point-lots?active=true|false&page=0&size=1~50` → 200 `PointMemberLots`(적립/충전 잔액 · 소멸 예정 · 미회수 · 묶음 한 쪽 · 최근 미회수 기록 5건.
 *        `active=true` 는 남아 있고 소멸일이 지나지 않은 묶음을 차감 순서로, `false` 는 모든 묶음을 최근 받은 순으로. 404 `MEMBER_NOT_FOUND`(탈퇴 포함). `point.read`)
 *   - `GET  /api/admin/settings/points` → 200 `PointSettingsResponse` (+ 읽기 전용 `adjustLargeThreshold`. `point.read`)
 *   - `PUT  /api/admin/settings/points` `PointSettingsValues`(14칸 모두) → 200 `PointSettingsResponse`
 *       (조정 문턱은 보내지 않는다. 칸마다 정수·범위 — 틀린 칸을 모아 400 `VALIDATION` + `fields.<칸>`(서버 문구), 하나라도 틀리면 아무것도 저장하지 않는다. 저장하면 15초 안에 모든 서버에 반영, 소급 없음. 감사 기록 `POINT_SETTINGS_UPDATE`(바뀐 값만). `point.settings`)
 *   공통 오류: 403 `PERMISSION_DENIED`.
 */
const PATH = {
    ledger: '/api/admin/points/ledger',
    candidates: '/api/admin/points/member-candidates',
    adjust: (memberId: string) => `/api/admin/members/${encodeURIComponent(memberId)}/points`,
    memberLots: (memberId: string) => `/api/admin/members/${encodeURIComponent(memberId)}/point-lots`,
    settings: '/api/admin/settings/points',
};

export function usePointApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 전체 원장 한 쪽 + 같은 조건의 합계 — `reasons` 는 반복 이름으로 간다 */
        ledger: (query: PointLedgerQuery): Promise<PointLedgerPage> => client.get<PointLedgerPage>(PATH.ledger, { query: { ...query } }),

        /** 조정 대상 회원 찾기 — 지금 잔액(적립/충전 나눔) 포함 */
        memberCandidates: (q: string): Promise<PointMemberCandidate[]> => client.get<PointMemberCandidate[]>(PATH.candidates, { query: { q } }),

        /** 수동 지급·차감(적립 포인트만) — 실제 반영값과 잔액을 돌려받는다 */
        adjust: (memberId: string, body: PointAdjustRequest): Promise<PointAdjustResponse> => client.post<PointAdjustResponse>(PATH.adjust(memberId), body),

        /** 회원 한 명의 포인트 묶음 한 쪽 + 적립/충전 잔액 · 소멸 예정 · 미회수 */
        memberLots: (memberId: string, query: PointMemberLotsQuery): Promise<PointMemberLots> => client.get<PointMemberLots>(PATH.memberLots(memberId), { query: { ...query } }),

        /** 포인트 정책(+ 읽기 전용 조정 문턱) */
        loadSettings: (): Promise<PointSettingsResponse> => client.get<PointSettingsResponse>(PATH.settings),

        /** 포인트 정책을 통째로 저장한다 — 저장된 값을 돌려준다 */
        saveSettings: (body: PointSettingsValues): Promise<PointSettingsResponse> => client.put<PointSettingsResponse>(PATH.settings, body),
    };
}
