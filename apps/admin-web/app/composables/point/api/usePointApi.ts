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
 * 서버 `PointAdminController`. 계약: `docs/features/point-contract.md`(community `docs/api-point.md` 「관리자 API」 + 전체 원장 조회·조정 대상 후보 조회).
 *
 * 오류는 모두 `ApiError`(useApiClient)로 던져진다. 쓰기는 CSRF. 권한: 조회 `point.read`, 정책 변경 `point.settings`, 수동 지급·차감 `point.adjust`. 응답은 `Cache-Control: no-store`.
 *   - `GET  /api/admin/points/ledger?from&to&kind&reasons&q&page&size` → 200 `PointLedgerPage` `{ items, total, summary: { earned, spent, adjustCount } }`
 *       (최신이 위. `from`·`to` 는 순간(UTC ISO), `reasons` 는 반복 이름, `q` 는 닉네임 부분·회원번호 같음·이메일(`member.pii` 있으면 부분, 없으면 전체 주소가 같을 때만), `page` 0부터, `size` 1~100.
 *        `summary` 는 같은 조건의 전체 줄 기준. 탈퇴 회원의 줄은 나오지 않는다. 틀린 조건은 칸을 모아 400 `VALIDATION` + `fields.from|to|kind|reasons|page|size`(시작 > 끝은 `fields.from`). `point.read`)
 *   - `GET  /api/admin/points/member-candidates?q=` → 200 `PointMemberCandidate[]` (탈퇴하지 않은 회원(정상·정지) 최대 20명, 최근 가입순, 지금 잔액 포함. 이메일은 `member.pii` 가 없으면 가린다. `point.adjust`)
 *   - `POST /api/admin/members/{id}/points` `{ delta, reason }` → 200 `{ applied, balance }`
 *       (delta 0 아닌 정수 −10000~10000 · reason 정규화 뒤 1~100자(회원 내역에 공개). 차감은 0P 까지라 `applied` 가 요청보다 작을 수 있고, 0 이면 원장 줄·알림이 없다.
 *        차감이 반영되면 회원에게 알림 `POINTS_REVOKED`. 감사 기록 `POINT_ADJUST`. 400 `VALIDATION` `fields.delta|reason` · 404 `MEMBER_NOT_FOUND`(탈퇴 포함). `point.adjust`)
 *   - `GET  /api/admin/settings/points` → 200 `PointSettingsResponse` (`point.read`)
 *   - `PUT  /api/admin/settings/points` `PointSettingsValues`(14칸 모두) → 200 `PointSettingsResponse`
 *       (칸마다 정수·범위 — 틀린 칸을 모아 400 `VALIDATION` + `fields.<칸>`(서버 문구), 하나라도 틀리면 아무것도 저장하지 않는다. 저장하면 15초 안에 모든 서버에 반영, 소급 없음. 감사 기록 `POINT_SETTINGS_UPDATE`(바뀐 값만). `point.settings`)
 *   공통 오류: 403 `PERMISSION_DENIED`.
 */
const PATH = {
    ledger: '/api/admin/points/ledger',
    candidates: '/api/admin/points/member-candidates',
    adjust: (memberId: string) => `/api/admin/members/${encodeURIComponent(memberId)}/points`,
    settings: '/api/admin/settings/points',
};

export function usePointApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 전체 원장 한 쪽 + 같은 조건의 합계 — `reasons` 는 반복 이름으로 간다 */
        ledger: (query: PointLedgerQuery): Promise<PointLedgerPage> => client.get<PointLedgerPage>(PATH.ledger, { query: { ...query } }),

        /** 조정 대상 회원 찾기 — 지금 잔액 포함 */
        memberCandidates: (q: string): Promise<PointMemberCandidate[]> => client.get<PointMemberCandidate[]>(PATH.candidates, { query: { q } }),

        /** 수동 지급·차감 — 실제 반영값과 잔액을 돌려받는다 */
        adjust: (memberId: string, body: PointAdjustRequest): Promise<PointAdjustResponse> => client.post<PointAdjustResponse>(PATH.adjust(memberId), body),

        /** 포인트 정책 */
        loadSettings: (): Promise<PointSettingsResponse> => client.get<PointSettingsResponse>(PATH.settings),

        /** 포인트 정책을 통째로 저장한다 — 저장된 값을 돌려준다 */
        saveSettings: (body: PointSettingsValues): Promise<PointSettingsResponse> => client.put<PointSettingsResponse>(PATH.settings, body),
    };
}
