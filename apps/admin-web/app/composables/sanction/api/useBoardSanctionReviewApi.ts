import type {
    BoardSanctionReviewCloseRequest,
    BoardSanctionReviewDetail,
    BoardSanctionReviewListView,
    BoardSanctionReviewQuery,
    BoardSanctionReviewSummary,
} from '~/composables/sanction/api/useBoardSanctionReviewApi.types';

/**
 * 관리자 게시판 제재 반복 검토 API — 서버 주소(`/api/admin/board-sanction-reviews…`)가 있는 **유일한 곳**이다. 상태는 없다. 계약 `docs/features/board-sanction-contract.md` 결정 3·9
 * (서버 `server/admin` `AdminBoardSanctionReviewController`).
 *   - `GET  /api/admin/board-sanction-reviews?status=&page=&size=` (`sanction.read`) → `BoardSanctionReviewListView`(상태 생략 = OPEN)
 *   - `GET  /summary` (`sanction.read`) → `{ open }`
 *   - `GET  /{id}` (`sanction.read`) → `BoardSanctionReviewDetail` · 404 `BOARD_SANCTION_REVIEW_NOT_FOUND`
 *   - `POST /{id}/close` `BoardSanctionReviewCloseRequest` (`sanction.manage`) → `200 BoardSanctionReviewDetail` · 409 `BOARD_SANCTION_REVIEW_CLOSED` · 400 `fields.result|memo|siteSanctionId|voidSanctionIds`
 * 서버 연결됨(2026-11-04).
 */
const ROOT = '/api/admin/board-sanction-reviews';
const PATH = {
    list: ROOT,
    summary: `${ROOT}/summary`,
    one: (id: string) => `${ROOT}/${encodeURIComponent(id)}`,
    close: (id: string) => `${ROOT}/${encodeURIComponent(id)}/close`,
};

export function useBoardSanctionReviewApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        list: (query: BoardSanctionReviewQuery, signal?: AbortSignal): Promise<BoardSanctionReviewListView> =>
            client.get<BoardSanctionReviewListView>(PATH.list, { query: { status: query.status, page: query.page, size: query.size }, signal }),

        /** 열린 건 수 — 탭 배지 */
        summary: (): Promise<BoardSanctionReviewSummary> => client.get<BoardSanctionReviewSummary>(PATH.summary),

        get: (id: string): Promise<BoardSanctionReviewDetail> => client.get<BoardSanctionReviewDetail>(PATH.one(id)),

        /** 닫기 — 그대로 둠 · 사이트 제재 부여(부여한 제재 번호) · 게시판 제재 무효(고른 제재) */
        close: (id: string, body: BoardSanctionReviewCloseRequest): Promise<BoardSanctionReviewDetail> => client.post<BoardSanctionReviewDetail>(PATH.close(id), body),
    };
}
