import { mockPointAdjust, mockPointLedger, mockPointMemberCandidates, mockPointSettings, mockPointSettingsSave } from '~/composables/point/api/mock/point-mock';
import type {
    PointAdjustRequest,
    PointAdjustResponse,
    PointLedgerPage,
    PointLedgerQuery,
    PointMemberCandidate,
    PointSettingsResponse,
    PointSettingsValues,
} from '~/composables/point/api/usePointApi.types';

/**
 * 포인트 API — 서버 주소 문자열(`/api/admin/points…`·`/api/admin/settings/points`·`/api/admin/members/{id}/points`)이 있는 **유일한 곳**이다. 상태는 없다.
 * community `docs/api-point.md` 「관리자 API」를 옮기고(최고 관리자 전용 → roboyam 권한 코드), 전체 원장 조회와 조정 대상 후보 조회를 더했다(그누보드 「포인트 관리」).
 *
 * 후보 계약(확정 전). 오류는 모두 `ApiError`(useApiClient)로 던져진다. 쓰기는 CSRF. 권한: 조회 `point.read`, 정책 변경 `point.settings`, 수동 지급·차감 `point.adjust`.
 *   - `GET  /api/admin/points/ledger?from&to&kind&reasons&q&page&size` → 200 `PointLedgerPage` `{ items, total, summary: { earned, spent, adjustCount } }`
 *       (최신이 위. `from`·`to` 는 순간(UTC ISO), `reasons` 는 반복 이름, `q` 는 닉네임·이메일·회원번호. `summary` 는 같은 조건의 전체 줄 기준. 틀린 조건 400 `VALIDATION` + `fields.<조건>`. `point.read`)
 *   - `GET  /api/admin/points/member-candidates?q=` → 200 `PointMemberCandidate[]` (조정 대상 회원 — 정상·정지 회원, 최대 20명, 지금 잔액 포함. 이메일은 `member.pii` 가 없으면 가린다. `point.adjust`)
 *   - `POST /api/admin/members/{id}/points` `{ delta, reason }` → 200 `{ applied, balance }`
 *       (delta 0 아닌 정수 −10000~10000 · reason 1~100자(회원 내역에 공개). 차감은 0P 까지라 `applied` 가 요청보다 작을 수 있고, 0 이면 원장 줄·알림이 없다.
 *        차감이 반영되면 회원에게 알림 `POINTS_REVOKED`. 감사 기록 `POINT_ADJUST`(대상 MEMBER + 회원 ID, 반영된 값 · 사유). 400 `VALIDATION` `fields.delta|reason` · 404 `MEMBER_NOT_FOUND`(탈퇴 포함). `point.adjust`)
 *   - `GET  /api/admin/settings/points` → 200 `PointSettingsResponse` (`point.read`)
 *   - `PUT  /api/admin/settings/points` `PointSettingsValues`(14칸 모두) → 200 `PointSettingsResponse`
 *       (칸마다 범위 검사 — 틀린 칸을 모아 400 `VALIDATION` + `fields.<칸>`, 하나라도 틀리면 아무것도 저장하지 않는다. 저장하면 15초 안에 모든 서버에 반영, 소급 없음. 감사 기록 `POINT_SETTINGS_UPDATE`(바뀐 값만). `point.settings`)
 *   공통 오류: 403 `PERMISSION_DENIED`.
 *
 * ⚠ MOCK: 지금은 서버가 없어서 `./mock/point-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄(과 `can`·목업 import)을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    ledger: '/api/admin/points/ledger',
    candidates: '/api/admin/points/member-candidates',
    adjust: (memberId: string) => `/api/admin/members/${encodeURIComponent(memberId)}/points`,
    settings: '/api/admin/settings/points',
};

export function usePointApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 목업이 서버처럼 내 권한(`point.read`·`point.settings`·`point.adjust`)으로 403 을 낸다
    const { can } = useAdminPermission();
    void PATH;

    return {
        /** 전체 원장 한 쪽 + 같은 조건의 합계 */
        ledger: (query: PointLedgerQuery): Promise<PointLedgerPage> =>
            // MOCK:
            mockPointLedger(can, query),
        // REAL: client.get<PointLedgerPage>(PATH.ledger, { query: { ...query } }),

        /** 조정 대상 회원 찾기 — 지금 잔액 포함 */
        memberCandidates: (q: string): Promise<PointMemberCandidate[]> =>
            // MOCK:
            mockPointMemberCandidates(can, q),
        // REAL: client.get<PointMemberCandidate[]>(PATH.candidates, { query: { q } }),

        /** 수동 지급·차감 — 실제 반영값과 잔액을 돌려받는다 */
        adjust: (memberId: string, body: PointAdjustRequest): Promise<PointAdjustResponse> =>
            // MOCK:
            mockPointAdjust(can, memberId, body),
        // REAL: client.post<PointAdjustResponse>(PATH.adjust(memberId), body),

        /** 포인트 정책 */
        loadSettings: (): Promise<PointSettingsResponse> =>
            // MOCK:
            mockPointSettings(can),
        // REAL: client.get<PointSettingsResponse>(PATH.settings),

        /** 포인트 정책을 통째로 저장한다 — 저장된 값을 돌려준다 */
        saveSettings: (body: PointSettingsValues): Promise<PointSettingsResponse> =>
            // MOCK:
            mockPointSettingsSave(can, body),
        // REAL: client.put<PointSettingsResponse>(PATH.settings, body),
    };
}
