import type { AdminBoardStaffAction, BoardStaffActionListView, BoardStaffActionQuery } from '~/composables/sanction/api/useBoardStaffActionApi.types';

/**
 * 관리자 스텝 처리 감독 API — 서버 주소(`/api/admin/board-staff-actions…`)가 있는 **유일한 곳**이다. 상태는 없다. 계약 `docs/features/board-staff-action-contract.md` 「API > 관리자」.
 *   - `GET  /api/admin/board-staff-actions?board=&kind=&restored=&q=&from=&to=&page=&size=` (`keeper-action.read`) → `BoardStaffActionListView` · 400 `fields.q|from|to`
 *   - `GET  /{id}` (`keeper-action.read`) → `AdminBoardStaffAction`
 *   - `POST /{id}/restore { reason }` (`keeper-action.restore`) → `200 AdminBoardStaffAction` · 400 `fields.reason`(1~200자) · 409 `BOARD_STAFF_ACTION_RESTORED`·`POST_STATE_INVALID`·`POST_PURGED`
 * 쓰기는 CSRF(`useApiClient` 가 붙인다). 목업 없이 실제 서버와 바로 말한다(서버 동시 구현 — 2026-11-04).
 */
const ROOT = '/api/admin/board-staff-actions';
const PATH = {
    list: ROOT,
    one: (id: string) => `${ROOT}/${encodeURIComponent(id)}`,
    restore: (id: string) => `${ROOT}/${encodeURIComponent(id)}/restore`,
};

export function useBoardStaffActionApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 목록 — 빈 조건·전체(`ALL`)는 보내지 않는다 */
        list: (query: BoardStaffActionQuery, signal?: AbortSignal): Promise<BoardStaffActionListView> =>
            client.get<BoardStaffActionListView>(PATH.list, {
                query: {
                    board: query.board || undefined,
                    kind: query.kind && query.kind !== 'ALL' ? query.kind : undefined,
                    restored: query.restored && query.restored !== 'ALL' ? query.restored : undefined,
                    q: query.q || undefined,
                    from: query.from || undefined,
                    to: query.to || undefined,
                    page: query.page,
                    size: query.size,
                },
                signal,
            }),

        /** 처리 하나 */
        get: (id: string): Promise<AdminBoardStaffAction> => client.get<AdminBoardStaffAction>(PATH.one(id)),

        /** 되돌리기 — 숨김 해제 · 글 복구 · 댓글 복구(작성자에게 알리지 않는다) */
        restore: (id: string, reason: string): Promise<AdminBoardStaffAction> => client.post<AdminBoardStaffAction>(PATH.restore(id), { reason }),
    };
}
