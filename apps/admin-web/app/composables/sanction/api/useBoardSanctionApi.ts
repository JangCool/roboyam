import { mockBoardSanctionGet, mockBoardSanctionLift, mockBoardSanctionList, mockBoardSanctionVoid } from '~/composables/sanction/api/mock/board-sanction-mock';
import type { BoardSanctionDetail, BoardSanctionListView, BoardSanctionQuery } from '~/composables/sanction/api/useBoardSanctionApi.types';

/**
 * 관리자 게시판 제재 API — 서버 주소(`/api/admin/board-sanctions…`)가 있는 **유일한 곳**이다. 상태는 없다. 계약 `docs/features/board-sanction-contract.md` 「API 후보 > 관리자」.
 *   - `GET  /api/admin/board-sanctions?scope=&boardId=&kind=&searchField=&q=&from=&to=&userId=&sort=&page=&size=` (`sanction.read`) → `BoardSanctionListView` · 400 `fields.q|from|to`
 *   - `GET  /{id}` (`sanction.read`) → `BoardSanctionDetail`(제재 + 그 회원의 게시판 제재 이력) · 404 `BOARD_SANCTION_NOT_FOUND`
 *   - `POST /{id}/lift { reason }` (`sanction.manage`) → `200 BoardSanctionDetail` · 409 `BOARD_SANCTION_NOT_LIFTABLE`
 *   - `POST /{id}/void { reason }` (`sanction.manage`) → `200 BoardSanctionDetail` · 409 `BOARD_SANCTION_ALREADY_VOIDED`
 * 쓰기는 CSRF(`useApiClient` 가 붙인다). 응답은 `Cache-Control: no-store`.
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/board-sanction-mock.ts` 를 부른다(`?mock=` 스위치는 그 파일 머리). 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const ROOT = '/api/admin/board-sanctions';
const PATH = {
    list: ROOT,
    one: (id: string) => `${ROOT}/${encodeURIComponent(id)}`,
    act: (id: string, action: 'lift' | 'void') => `${ROOT}/${encodeURIComponent(id)}/${action}`,
};

export function useBoardSanctionApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH;

    return {
        /** 목록 — 빈 조건은 보내지 않는다 */
        list: (query: BoardSanctionQuery, signal?: AbortSignal): Promise<BoardSanctionListView> =>
            // MOCK:
            (void signal, mockBoardSanctionList(query)),
        // REAL: client.get<BoardSanctionListView>(PATH.list, {
        // REAL:     query: {
        // REAL:         scope: query.scope && query.scope !== 'ALL' ? query.scope : undefined,
        // REAL:         boardId: query.boardId || undefined,
        // REAL:         kind: query.kind || undefined,
        // REAL:         q: query.q || undefined,
        // REAL:         searchField: query.q ? query.searchField : undefined,
        // REAL:         from: query.from || undefined,
        // REAL:         to: query.to || undefined,
        // REAL:         userId: query.userId || undefined,
        // REAL:         sort: query.sort,
        // REAL:         page: query.page,
        // REAL:         size: query.size,
        // REAL:     },
        // REAL:     signal,
        // REAL: }),

        /** 제재 하나 + 그 회원의 게시판 제재 이력 */
        get: (id: string): Promise<BoardSanctionDetail> =>
            // MOCK:
            mockBoardSanctionGet(id),
        // REAL: client.get<BoardSanctionDetail>(PATH.one(id)),

        /** 진행 중 쓰기 제한 해제 → LIFTED */
        lift: (id: string, reason: string): Promise<BoardSanctionDetail> =>
            // MOCK:
            mockBoardSanctionLift(id, reason),
        // REAL: client.post<BoardSanctionDetail>(PATH.act(id, 'lift'), { reason }),

        /** 무효 → VOIDED(진행 중 쓰기 제한은 바로 풀리고 반복 검토·권장 수에서 빠진다) */
        void: (id: string, reason: string): Promise<BoardSanctionDetail> =>
            // MOCK:
            mockBoardSanctionVoid(id, reason),
        // REAL: client.post<BoardSanctionDetail>(PATH.act(id, 'void'), { reason }),
    };
}
