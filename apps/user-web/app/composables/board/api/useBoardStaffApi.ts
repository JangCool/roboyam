import type {
    BoardSanctionIssued,
    BoardSanctionIssueRequest,
    BoardSanctionLiftRequest,
    BoardSanctionPreview,
    BoardStaffActionDone,
    BoardStaffActionPage,
    BoardStaffActionQuery,
    BoardStaffActionRestoreRequest,
    BoardStaffActionRow,
    BoardStaffCommentDeleteRequest,
    BoardStaffPostActionRequest,
    BoardStaffSanctionPage,
    BoardStaffSanctionQuery,
    BoardStaffSanctionRow,
} from '~/composables/board/api/useBoardStaffApi.types';

/**
 * 게시판 매니저·스텝 도구 API — 서버 주소 문자열(`/api/board-staff/…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: `docs/features/board-sanction-contract.md` · `docs/features/board-staff-action-contract.md`(서버 `server/api` `BoardStaffController`).
 * 로그인 + 그 게시판 매니저·스텝(역할 권한) — 처리할 때마다 서버가 다시 판정한다.
 *
 * 제재(`board.sanction.issue`):
 *  - `GET  /api/board-staff/sanctions/preview?boardId=&userId=` → `BoardSanctionPreview` · `403 BOARD_STAFF_FORBIDDEN` · `409 BOARD_STAFF_TARGET_PROTECTED`
 *  - `POST /api/board-staff/sanctions` `BoardSanctionIssueRequest` → `201 BoardSanctionIssued` · `400 VALIDATION fields.kind|reasonCode|reasonText|postId|commentId` · `403 BOARD_STAFF_FORBIDDEN`
 *    · `409 BOARD_STAFF_TARGET_PROTECTED` · `409 BOARD_STAFF_ALREADY_RESTRICTED` · `409 BOARD_STAFF_DAILY_LIMIT`(본문 최상위 `limit`·`used`·`resetsAt`)
 *  - `GET  /api/board-staff/boards/{boardId}/sanctions?scope=ALL|ACTIVE|ENDED&page=`(1부터, 20개) → `BoardStaffSanctionPage` · 매니저·스텝이 아니면 `404 BOARD_STAFF_NOT_FOUND`
 *  - `POST /api/board-staff/sanctions/{id}/lift` `{ reason }`(1~200자) → 바뀐 줄 · `400 fields.reason` · 매니저 + `board.staff-action.restore` 만(`403`) · `404 BOARD_SANCTION_NOT_FOUND`
 *    · 진행 중 쓰기 제한만(`409 BOARD_SANCTION_NOT_LIFTABLE`)
 *
 * 스텝 처리(`board.post.hide`·`board.post.delete`·`board.comment.delete`):
 *  - `POST /api/board-staff/posts/{postId}/hide` · `…/delete` `{ reasonCode }` → `BoardStaffActionDone`(글 한도)
 *  - `POST /api/board-staff/comments/{commentId}/delete` `{ postId, reasonCode }` → `BoardStaffActionDone`(댓글 한도)
 *    · 거절(판정 순서): 404 `POST_NOT_FOUND`·`COMMENT_NOT_FOUND` → 403 `BOARD_STAFF_FORBIDDEN` → 409 `BOARD_STAFF_TARGET_PROTECTED` → 409 `POST_STATE_INVALID`·`COMMENT_STATE_INVALID`
 *      → 409 `BOARD_STAFF_DAILY_LIMIT`(본문 최상위 `kind` POST|COMMENT·`limit`·`used`·`resetsAt`) · 입력 `400 fields.reasonCode`
 *  - `GET  /api/board-staff/boards/{boardId}/actions?kind=ALL|POST|COMMENT&page=`(1부터, 20개) → `BoardStaffActionPage` · 매니저·스텝이 아니면 `404 BOARD_STAFF_NOT_FOUND`
 *  - `POST /api/board-staff/actions/{id}/restore` `{ reason }`(1~200자) → 바뀐 줄 · 매니저만(`403 BOARD_STAFF_FORBIDDEN`) · `409 BOARD_STAFF_ACTION_RESTORED`·`POST_STATE_INVALID`·`POST_PURGED`
 *
 * 제재 서버 연결됨(2026-11-04). 스텝 처리는 서버와 같은 계약으로 동시에 만든다(목업 없음).
 */
const PATH = {
    preview: '/api/board-staff/sanctions/preview',
    sanctions: '/api/board-staff/sanctions',
    boardSanctions: (boardId: string) => `/api/board-staff/boards/${encodeURIComponent(boardId)}/sanctions`,
    lift: (id: string) => `/api/board-staff/sanctions/${encodeURIComponent(id)}/lift`,
    postHide: (postId: string) => `/api/board-staff/posts/${encodeURIComponent(postId)}/hide`,
    postDelete: (postId: string) => `/api/board-staff/posts/${encodeURIComponent(postId)}/delete`,
    commentDelete: (commentId: string) => `/api/board-staff/comments/${encodeURIComponent(commentId)}/delete`,
    boardActions: (boardId: string) => `/api/board-staff/boards/${encodeURIComponent(boardId)}/actions`,
    restore: (id: string) => `/api/board-staff/actions/${encodeURIComponent(id)}/restore`,
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

        /** 글 숨기기 — 응답은 남은 글 한도 */
        hidePost: (postId: string, body: BoardStaffPostActionRequest): Promise<BoardStaffActionDone> => client.post<BoardStaffActionDone>(PATH.postHide(postId), body),

        /** 글 삭제 — 응답은 남은 글 한도 */
        deletePost: (postId: string, body: BoardStaffPostActionRequest): Promise<BoardStaffActionDone> => client.post<BoardStaffActionDone>(PATH.postDelete(postId), body),

        /** 댓글 삭제 — 응답은 남은 댓글 한도 */
        deleteComment: (commentId: string, body: BoardStaffCommentDeleteRequest): Promise<BoardStaffActionDone> =>
            client.post<BoardStaffActionDone>(PATH.commentDelete(commentId), body),

        /** 처리 내역 한 쪽 — 매니저는 그 게시판 전체(처리자 포함), 스텝은 본인 것만 */
        actions: (boardId: string, query: BoardStaffActionQuery, signal?: AbortSignal): Promise<BoardStaffActionPage> =>
            client.get<BoardStaffActionPage>(PATH.boardActions(boardId), { query: { kind: query.kind, page: query.page }, signal }),

        /** 처리 되돌리기(매니저) — 응답은 바뀐 줄 */
        restore: (id: string, body: BoardStaffActionRestoreRequest): Promise<BoardStaffActionRow> => client.post<BoardStaffActionRow>(PATH.restore(id), body),
    };
}
