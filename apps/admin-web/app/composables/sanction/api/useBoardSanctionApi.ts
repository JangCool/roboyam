import type { BoardSanctionDetail, BoardSanctionListView, BoardSanctionQuery } from '~/composables/sanction/api/useBoardSanctionApi.types';

/**
 * 관리자 게시판 제재 API — 서버 주소(`/api/admin/board-sanctions…`)가 있는 **유일한 곳**이다. 상태는 없다. 계약 `docs/features/board-sanction-contract.md`(서버 `server/admin` `AdminBoardSanctionController`).
 *   - `GET  /api/admin/board-sanctions?scope=&boardId=&kind=&searchField=&q=&from=&to=&userId=&sort=&page=&size=` (`sanction.read`) → `BoardSanctionListView` · 400 `fields.q|from|to`
 *   - `GET  /{id}` (`sanction.read`) → `BoardSanctionDetail`(제재 + 그 회원의 게시판 제재 이력) · 404 `BOARD_SANCTION_NOT_FOUND`
 *   - `POST /{id}/lift { reason }` (`sanction.manage`) → `200 BoardSanctionDetail` · 400 `fields.reason`(1~200자) · 409 `BOARD_SANCTION_NOT_LIFTABLE`
 *   - `POST /{id}/void { reason }` (`sanction.manage`) → `200 BoardSanctionDetail` · 400 `fields.reason`(1~200자) · 409 `BOARD_SANCTION_ALREADY_VOIDED` — 대기 이의제기는 같은 트랜잭션에서 인용(결정 10)
 * 쓰기는 CSRF(`useApiClient` 가 붙인다). 응답은 `Cache-Control: no-store`. 서버 연결됨(2026-11-04).
 */
const ROOT = '/api/admin/board-sanctions';
const PATH = {
    list: ROOT,
    one: (id: string) => `${ROOT}/${encodeURIComponent(id)}`,
    act: (id: string, action: 'lift' | 'void') => `${ROOT}/${encodeURIComponent(id)}/${action}`,
};

export function useBoardSanctionApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 목록 — 빈 조건은 보내지 않는다 */
        list: (query: BoardSanctionQuery, signal?: AbortSignal): Promise<BoardSanctionListView> =>
            client.get<BoardSanctionListView>(PATH.list, {
                query: {
                    scope: query.scope && query.scope !== 'ALL' ? query.scope : undefined,
                    boardId: query.boardId || undefined,
                    kind: query.kind || undefined,
                    q: query.q || undefined,
                    searchField: query.q ? query.searchField : undefined,
                    from: query.from || undefined,
                    to: query.to || undefined,
                    userId: query.userId || undefined,
                    sort: query.sort,
                    page: query.page,
                    size: query.size,
                },
                signal,
            }),

        /** 제재 하나 + 그 회원의 게시판 제재 이력 */
        get: (id: string): Promise<BoardSanctionDetail> => client.get<BoardSanctionDetail>(PATH.one(id)),

        /** 진행 중 쓰기 제한 해제 → LIFTED */
        lift: (id: string, reason: string): Promise<BoardSanctionDetail> => client.post<BoardSanctionDetail>(PATH.act(id, 'lift'), { reason }),

        /** 무효 → VOIDED(진행 중 쓰기 제한은 바로 풀리고 반복 검토·권장 수에서 빠진다) */
        void: (id: string, reason: string): Promise<BoardSanctionDetail> => client.post<BoardSanctionDetail>(PATH.act(id, 'void'), { reason }),
    };
}
