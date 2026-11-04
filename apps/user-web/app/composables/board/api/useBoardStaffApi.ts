import type {
    BoardSanctionIssued,
    BoardSanctionIssueRequest,
    BoardSanctionLiftRequest,
    BoardSanctionPreview,
    BoardStaffSanctionPage,
    BoardStaffSanctionQuery,
    BoardStaffSanctionRow,
} from '~/composables/board/api/useBoardStaffApi.types';

/**
 * 게시판 매니저·스텝 도구 API — 서버 주소 문자열(`/api/board-staff/…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: `docs/features/board-sanction-contract.md`(서버 `server/api` `BoardStaffController`). 로그인 + 그 게시판 매니저·스텝(`board.sanction.issue`) — 처리할 때마다 서버가 다시 판정한다.
 *
 *  - `GET  /api/board-staff/sanctions/preview?boardId=&userId=` → `BoardSanctionPreview` · `403 BOARD_STAFF_FORBIDDEN` · `409 BOARD_STAFF_TARGET_PROTECTED`
 *  - `POST /api/board-staff/sanctions` `BoardSanctionIssueRequest` → `201 BoardSanctionIssued` · `400 VALIDATION fields.kind|reasonCode|reasonText|postId|commentId` · `403 BOARD_STAFF_FORBIDDEN`
 *    · `409 BOARD_STAFF_TARGET_PROTECTED` · `409 BOARD_STAFF_ALREADY_RESTRICTED` · `409 BOARD_STAFF_DAILY_LIMIT`(본문 최상위 `limit`·`used`·`resetsAt`)
 *  - `GET  /api/board-staff/boards/{boardId}/sanctions?scope=ALL|ACTIVE|ENDED&page=`(1부터, 20개) → `BoardStaffSanctionPage` · 매니저·스텝이 아니면 `404 BOARD_STAFF_NOT_FOUND`
 *  - `POST /api/board-staff/sanctions/{id}/lift` `{ reason }`(1~200자) → 바뀐 줄 · `400 fields.reason` · 매니저 + `board.staff-action.restore` 만(`403`) · `404 BOARD_SANCTION_NOT_FOUND`
 *    · 진행 중 쓰기 제한만(`409 BOARD_SANCTION_NOT_LIFTABLE`)
 *
 * 서버 연결됨(2026-11-04).
 */
const PATH = {
    preview: '/api/board-staff/sanctions/preview',
    sanctions: '/api/board-staff/sanctions',
    boardSanctions: (boardId: string) => `/api/board-staff/boards/${encodeURIComponent(boardId)}/sanctions`,
    lift: (id: string) => `/api/board-staff/sanctions/${encodeURIComponent(id)}/lift`,
};

export function useBoardStaffApi() {
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 제재 대화창의 현황 — 그 게시판에서 대상이 받은 제재 수·진행 중 제한·권장 단계·오늘 남은 한도 */
        preview: (boardId: string, userId: string, signal?: AbortSignal): Promise<BoardSanctionPreview> =>
            client.get<BoardSanctionPreview>(PATH.preview, { query: { boardId, userId }, signal }),

        /** 제재 주기 */
        issue: (body: BoardSanctionIssueRequest): Promise<BoardSanctionIssued> => client.post<BoardSanctionIssued>(PATH.sanctions, body),

        /** 제재 내역 한 쪽 — 매니저는 그 게시판 전체, 스텝은 내가 준 것만 */
        list: (boardId: string, query: BoardStaffSanctionQuery, signal?: AbortSignal): Promise<BoardStaffSanctionPage> =>
            client.get<BoardStaffSanctionPage>(PATH.boardSanctions(boardId), { query: { scope: query.scope, page: query.page }, signal }),

        /** 진행 중인 쓰기 제한 해제(매니저) — 응답은 바뀐 줄 */
        lift: (id: string, body: BoardSanctionLiftRequest): Promise<BoardStaffSanctionRow> => client.post<BoardStaffSanctionRow>(PATH.lift(id), body),
    };
}
