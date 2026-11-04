import { mockBoardAcknowledge, mockBoardAppeal, mockWithBoardSanctions } from '~/composables/sanction/api/mock/sanction-mock';
import type { AppealCreated, AppealRequest, MySanctions } from '~/composables/sanction/api/useSanctionApi.types';

/**
 * 내 제재·이의제기 API(회원 쪽) — 서버 주소 문자열(`/api/me/sanctions`·`/api/me/appeals`·`/api/me/board-sanctions`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: `docs/features/member-sanction-contract.md`(서버 `server/api` `MySanctionController`). **로그인만 하면 쓴다** — 정지·영구 정지 중에도, 이메일 인증 전에도(비로그인 `401`).
 *
 *  - `GET /api/me/sanctions` → `MySanctions` — 읽기 전에 서버가 끝이 지난 정지를 먼저 끝낸다(지연 만료)
 *  - `POST /api/me/sanctions/{id}/acknowledge` → `204`(멱등) · 내 것이 아니거나 없으면 `404 SANCTION_NOT_FOUND`
 *  - `POST /api/me/appeals` `{ sanctionId, reason }` → `201 AppealCreated` · `400 VALIDATION fields.reason`(10~500자) · `404 SANCTION_NOT_FOUND` · `409 APPEAL_ALREADY`(기각 뒤에도) · `409 SANCTION_NOT_APPEALABLE`(무효가 된 제재)
 *    · `409 APPEAL_EXPIRED`(제재 시작 후 30일이 지남 — `MySanction.appealDeadline`, member-sanction-contract 결정 3)
 * 게시판 제재(`docs/features/board-sanction-contract.md` 「API 후보 > 공개」) — ⚠ MOCK: 서버가 아직 없어 `./mock/sanction-mock.ts` 가 채운다.
 *  - `GET /api/me/sanctions` 의 `boardSanctions[]`(처리자 없음)
 *  - `POST /api/me/board-sanctions/{id}/acknowledge` → `204`(확인 안 한 경고)
 *  - `POST /api/me/appeals` `{ boardSanctionId, reason }`(`sanctionId` 와 둘 중 하나) — 사이트 제재와 같은 오류
 * 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 * 로그인 정보의 요약(`restriction` — 전역 배너·로그인 직후 안내)은 `useAuthApi().me()` 가 준다.
 */
const PATH = {
    mine: '/api/me/sanctions',
    acknowledge: (id: string) => `/api/me/sanctions/${encodeURIComponent(id)}/acknowledge`,
    boardAcknowledge: (id: string) => `/api/me/board-sanctions/${encodeURIComponent(id)}/acknowledge`,
    appeals: '/api/me/appeals',
};

export function useSanctionApi() {
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 제재 — 계정 상태 · 유효한 경고 횟수 · 진행 중인 정지 · 이력 · 게시판 제재 */
        mine: (signal?: AbortSignal): Promise<MySanctions> =>
            client
                .get<MySanctions>(PATH.mine, { signal })
                // MOCK: 서버가 아직 주지 않는 `boardSanctions` 를 채운다(`?mock=board-sanctions|board-restricted`)
                .then(mockWithBoardSanctions),
        // REAL: mine: (signal?: AbortSignal): Promise<MySanctions> => client.get<MySanctions>(PATH.mine, { signal }),

        /** 경고 안내를 확인했다고 표시한다 */
        acknowledge: (id: string): Promise<void> => client.post<void>(PATH.acknowledge(id)),

        /** 게시판 제재 경고를 확인했다고 표시한다(멱등) */
        acknowledgeBoard: (id: string): Promise<void> =>
            // MOCK:
            mockBoardAcknowledge(id),
        // REAL: client.post<void>(PATH.boardAcknowledge(id)),

        /** 이의제기 접수 — 제재 하나에 1번. 게시판 제재(`boardSanctionId`)는 목업으로 보낸다 */
        appeal: (request: AppealRequest): Promise<AppealCreated> =>
            'boardSanctionId' in request
                ? // MOCK:
                  mockBoardAppeal(request.boardSanctionId, request.reason)
                : // REAL: (게시판 제재도 같은 줄로)
                  client.post<AppealCreated>(PATH.appeals, request),
    };
}
