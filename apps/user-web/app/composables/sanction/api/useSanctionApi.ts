import type { AppealCreated, AppealRequest, MySanctions } from '~/composables/sanction/api/useSanctionApi.types';

/**
 * 내 제재·이의제기 API(회원 쪽) — 서버 주소 문자열(`/api/me/sanctions`·`/api/me/appeals`·`/api/me/board-sanctions`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: `docs/features/member-sanction-contract.md`(서버 `server/api` `MySanctionController`). **로그인만 하면 쓴다** — 정지·영구 정지 중에도, 이메일 인증 전에도(비로그인 `401`).
 *
 *  - `GET /api/me/sanctions` → `MySanctions` — 읽기 전에 서버가 끝이 지난 정지를 먼저 끝낸다(지연 만료). 게시판 제재 `boardSanctions[]`(최대 50, 처리자 없음)도 함께
 *  - `POST /api/me/sanctions/{id}/acknowledge` → `204`(멱등) · 내 것이 아니거나 없으면 `404 SANCTION_NOT_FOUND`
 *  - `POST /api/me/board-sanctions/{id}/acknowledge` → `204`(멱등, 게시판 경고) · 내 것이 아니거나 없으면 `404 BOARD_SANCTION_NOT_FOUND`(board-sanction-contract)
 *  - `POST /api/me/appeals` `{ sanctionId | boardSanctionId, reason }`(둘 중 하나 — 둘 다면 `400 fields.boardSanctionId`) → `201 AppealCreated` · `400 VALIDATION fields.reason`(10~500자)
 *    · `404 SANCTION_NOT_FOUND`·`BOARD_SANCTION_NOT_FOUND` · `409 APPEAL_ALREADY`(기각 뒤에도) · `409 SANCTION_NOT_APPEALABLE`(무효가 된 제재)
 *    · `409 APPEAL_EXPIRED`(제재 시작 후 30일이 지남 — `appealDeadline`, member-sanction-contract 결정 3)
 * 로그인 정보의 요약(`restriction` — 전역 배너·로그인 직후 안내)은 `useAuthApi().me()` 가 준다(게시판 경고는 `pendingNotices` 에 세지 않는다).
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
        mine: (signal?: AbortSignal): Promise<MySanctions> => client.get<MySanctions>(PATH.mine, { signal }),

        /** 경고 안내를 확인했다고 표시한다 */
        acknowledge: (id: string): Promise<void> => client.post<void>(PATH.acknowledge(id)),

        /** 게시판 제재 경고를 확인했다고 표시한다(멱등) */
        acknowledgeBoard: (id: string): Promise<void> => client.post<void>(PATH.boardAcknowledge(id)),

        /** 이의제기 접수 — 제재 하나에 1번(사이트 제재 `sanctionId` 또는 게시판 제재 `boardSanctionId`) */
        appeal: (request: AppealRequest): Promise<AppealCreated> => client.post<AppealCreated>(PATH.appeals, request),
    };
}
