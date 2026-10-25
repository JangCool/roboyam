import { mockAcknowledge, mockAppeal, mockMySanctions } from '~/composables/sanction/api/mock/sanction-mock';
import type { AppealCreated, AppealRequest, MySanctions } from '~/composables/sanction/api/useSanctionApi.types';

/**
 * 내 제재·이의제기 API(회원 쪽) — 서버 주소 문자열(`/api/me/sanctions`·`/api/me/appeals`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 후보: `docs/features/member-activity-contract.md`(모양은 community `docs/api-sanction.md` 2절). **로그인만 하면 쓴다** — 정지·영구 정지 중에도, 이메일 인증 전에도(비로그인 `401`).
 *
 *  - `GET /api/me/sanctions` → `MySanctions`
 *  - `POST /api/me/sanctions/{id}/acknowledge` → `204`(멱등) · 내 것이 아니거나 없으면 `404 SANCTION_NOT_FOUND`
 *  - `POST /api/me/appeals` `{ sanctionId, reason }` → `201 AppealCreated` · `400 VALIDATION fields.reason`(10~500자) · `404 SANCTION_NOT_FOUND` · `409 APPEAL_ALREADY`(기각 뒤에도) · `409 SANCTION_NOT_APPEALABLE`(무효가 된 제재)
 * 로그인 정보의 요약(`restriction` — 전역 배너·로그인 직후 안내)은 `useAuthApi().me()` 가 준다(이미 서버에 있다).
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/sanction-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    mine: '/api/me/sanctions',
    acknowledge: (id: string) => `/api/me/sanctions/${encodeURIComponent(id)}/acknowledge`,
    appeals: '/api/me/appeals',
};

export function useSanctionApi() {
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 목업의 처음 상태는 로그인 정보의 `restriction` 을 따른다
    const { restriction } = useAuth();
    void PATH;

    return {
        /** 내 제재 — 계정 상태 · 유효한 경고 횟수 · 진행 중인 정지 · 이력 */
        mine: (signal?: AbortSignal): Promise<MySanctions> =>
            // MOCK:
            (void signal, mockMySanctions(restriction.value ?? null)),
        // REAL: client.get<MySanctions>(PATH.mine, { signal }),

        /** 경고 안내를 확인했다고 표시한다 */
        acknowledge: (id: string): Promise<void> =>
            // MOCK:
            mockAcknowledge(restriction.value ?? null, id),
        // REAL: client.post<void>(PATH.acknowledge(id)),

        /** 이의제기 접수 — 제재 하나에 1번 */
        appeal: (request: AppealRequest): Promise<AppealCreated> =>
            // MOCK:
            mockAppeal(restriction.value ?? null, request),
        // REAL: client.post<AppealCreated>(PATH.appeals, request),
    };
}
