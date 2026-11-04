import { mockReviewClose, mockReviewGet, mockReviewList, mockReviewSummary } from '~/composables/sanction/api/mock/board-sanction-mock';
import type {
    BoardSanctionReviewCloseRequest,
    BoardSanctionReviewDetail,
    BoardSanctionReviewListView,
    BoardSanctionReviewQuery,
    BoardSanctionReviewSummary,
} from '~/composables/sanction/api/useBoardSanctionReviewApi.types';

/**
 * 관리자 게시판 제재 반복 검토 API — 서버 주소(`/api/admin/board-sanction-reviews…`)가 있는 **유일한 곳**이다. 상태는 없다. 계약 `docs/features/board-sanction-contract.md` 결정 3.
 *   - `GET  /api/admin/board-sanction-reviews?status=&page=&size=` (`sanction.read`) → `BoardSanctionReviewListView`(상태 생략 = OPEN)
 *   - `GET  /summary` (`sanction.read`) → `{ open }`
 *   - `GET  /{id}` (`sanction.read`) → `BoardSanctionReviewDetail` · 404 `BOARD_SANCTION_REVIEW_NOT_FOUND`
 *   - `POST /{id}/close` `BoardSanctionReviewCloseRequest` (`sanction.manage`) → `200 BoardSanctionReviewDetail` · 409 `BOARD_SANCTION_REVIEW_CLOSED` · 400 `fields.memo|siteSanctionId|voidSanctionIds`
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/board-sanction-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const ROOT = '/api/admin/board-sanction-reviews';
const PATH = {
    list: ROOT,
    summary: `${ROOT}/summary`,
    one: (id: string) => `${ROOT}/${encodeURIComponent(id)}`,
    close: (id: string) => `${ROOT}/${encodeURIComponent(id)}/close`,
};

export function useBoardSanctionReviewApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH;

    return {
        list: (query: BoardSanctionReviewQuery, signal?: AbortSignal): Promise<BoardSanctionReviewListView> =>
            // MOCK:
            (void signal, mockReviewList(query)),
        // REAL: client.get<BoardSanctionReviewListView>(PATH.list, { query: { status: query.status, page: query.page, size: query.size }, signal }),

        /** 열린 건 수 — 탭 배지 */
        summary: (): Promise<BoardSanctionReviewSummary> =>
            // MOCK:
            mockReviewSummary(),
        // REAL: client.get<BoardSanctionReviewSummary>(PATH.summary),

        get: (id: string): Promise<BoardSanctionReviewDetail> =>
            // MOCK:
            mockReviewGet(id),
        // REAL: client.get<BoardSanctionReviewDetail>(PATH.one(id)),

        /** 닫기 — 그대로 둠 · 사이트 제재 부여(부여한 제재 번호) · 게시판 제재 무효(고른 제재) */
        close: (id: string, body: BoardSanctionReviewCloseRequest): Promise<BoardSanctionReviewDetail> =>
            // MOCK:
            mockReviewClose(id, body),
        // REAL: client.post<BoardSanctionReviewDetail>(PATH.close(id), body),
    };
}
