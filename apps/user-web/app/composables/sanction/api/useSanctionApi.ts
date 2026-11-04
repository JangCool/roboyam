import type { AppealCreated, AppealRequest, MySanctions } from '~/composables/sanction/api/useSanctionApi.types';

/**
 * 내 제재·이의제기 API(회원 쪽) — 서버 주소 문자열(`/api/me/sanctions`·`/api/me/appeals`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: `docs/features/member-sanction-contract.md`(서버 `server/api` `MySanctionController`). **로그인만 하면 쓴다** — 정지·영구 정지 중에도, 이메일 인증 전에도(비로그인 `401`).
 *
 *  - `GET /api/me/sanctions` → `MySanctions` — 읽기 전에 서버가 끝이 지난 정지를 먼저 끝낸다(지연 만료)
 *  - `POST /api/me/sanctions/{id}/acknowledge` → `204`(멱등) · 내 것이 아니거나 없으면 `404 SANCTION_NOT_FOUND`
 *  - `POST /api/me/appeals` `{ sanctionId, reason }` → `201 AppealCreated` · `400 VALIDATION fields.reason`(10~500자) · `404 SANCTION_NOT_FOUND` · `409 APPEAL_ALREADY`(기각 뒤에도) · `409 SANCTION_NOT_APPEALABLE`(무효가 된 제재)
 *    · `409 APPEAL_EXPIRED`(제재 시작 후 30일이 지남 — `MySanction.appealDeadline`, member-sanction-contract 결정 3)
 * 로그인 정보의 요약(`restriction` — 전역 배너·로그인 직후 안내)은 `useAuthApi().me()` 가 준다.
 */
const PATH = {
    mine: '/api/me/sanctions',
    acknowledge: (id: string) => `/api/me/sanctions/${encodeURIComponent(id)}/acknowledge`,
    appeals: '/api/me/appeals',
};

export function useSanctionApi() {
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 제재 — 계정 상태 · 유효한 경고 횟수 · 진행 중인 정지 · 이력 */
        mine: (signal?: AbortSignal): Promise<MySanctions> => client.get<MySanctions>(PATH.mine, { signal }),

        /** 경고 안내를 확인했다고 표시한다 */
        acknowledge: (id: string): Promise<void> => client.post<void>(PATH.acknowledge(id)),

        /** 이의제기 접수 — 제재 하나에 1번 */
        appeal: (request: AppealRequest): Promise<AppealCreated> => client.post<AppealCreated>(PATH.appeals, request),
    };
}
