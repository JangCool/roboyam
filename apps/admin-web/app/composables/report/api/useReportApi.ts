import type { AdminPostBoardOptionsView } from '~/composables/post/api/usePostApi.types';
import type {
    ReportBulkDismissRequest,
    ReportBulkResult,
    ReportCaseDetail,
    ReportCaseListView,
    ReportCaseQuery,
    ReportDismissRequest,
    ReportResolveRequest,
    ReportSummary,
} from '~/composables/report/api/useReportApi.types';

/**
 * 신고 관리 API — 서버 주소(`/api/admin/reports…`)가 있는 **유일한 곳**이다. 상태는 없다. 계약: `docs/features/report-admin-contract.md` 「API」.
 *   - `GET  /api/admin/reports?status=&targetType=&reason=&searchField=&q=&from=&to=&board=&postId=&sort=&page=&size=` (`report.read`) → `ReportCaseListView`
 *       · 조건이 틀리면 400 `fields.q`·`fields.searchField`·`fields.from`·`fields.to`(가정 — 게시글 관리와 같은 규칙)
 *   - `GET  /summary` (`report.read`) → `{ pending, inReview }`
 *   - `GET  /board-options` (`report.read`) → `{ boards: [...] }` — 게시판 고르개. 모양은 게시글 관리 `GET /api/admin/posts/board-options` 와 같다(`AdminPostBoardOptionsView`)
 *   - `GET  /{id}` (`report.read`) → 상세 — PENDING 이면 열람과 함께 IN_REVIEW · 404 `REPORT_CASE_NOT_FOUND`
 *   - `POST /{id}/resolve { action, reasonCode, note }` (`report.manage`) → 200 상세 · 409 `REPORT_CASE_CLOSED`·`REPORT_ACTION_INVALID` · 400 `fields.action|reasonCode|note`
 *   - `POST /{id}/dismiss { reasonCode?, note?, falseReport }` (`report.manage`) → 200 상세 · 409 `REPORT_CASE_CLOSED`
 *   - `POST /dismiss { ids(1~50), falseReport, note? }` (`report.manage`) → `{ done, failed: [{id, code}] }` — 사건마다 따로
 *   - `POST /{id}/reopen` (`report.manage`) → 200 상세 · 409 `REPORT_CASE_CLOSED`(조치한 사건)·`REPORT_CASE_OPEN_EXISTS`
 * 쓰기는 CSRF(`useApiClient` 가 붙인다). 응답은 `Cache-Control: no-store`.
 */
const ROOT = '/api/admin/reports';
const PATH = {
    list: ROOT,
    summary: `${ROOT}/summary`,
    boardOptions: `${ROOT}/board-options`,
    bulkDismiss: `${ROOT}/dismiss`,
    one: (id: string) => `${ROOT}/${encodeURIComponent(id)}`,
    act: (id: string, action: 'resolve' | 'dismiss' | 'reopen') => `${ROOT}/${encodeURIComponent(id)}/${action}`,
};

export function useReportApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        // REAL:
        /** 사건 목록 — 빈 조건은 보내지 않는다 */
        list: (query: ReportCaseQuery, signal?: AbortSignal): Promise<ReportCaseListView> =>
            client.get<ReportCaseListView>(PATH.list, {
                query: {
                    status: query.status && query.status !== 'OPEN' ? query.status : undefined,
                    targetType: query.targetType || undefined,
                    reason: query.reason || undefined,
                    q: query.q || undefined,
                    searchField: query.q ? query.searchField : undefined,
                    from: query.from || undefined,
                    to: query.to || undefined,
                    board: query.board || undefined,
                    postId: query.postId || undefined,
                    sort: query.sort,
                    page: query.page,
                    size: query.size,
                },
                signal,
            }),

        // REAL:
        /** 대기·검토 중 건수 */
        summary: (): Promise<ReportSummary> => client.get<ReportSummary>(PATH.summary),

        // REAL:
        /** 게시판 고르개 — 게시판·말머리(모양은 게시글 관리 선택지와 같다) */
        boardOptions: (): Promise<AdminPostBoardOptionsView> => client.get<AdminPostBoardOptionsView>(PATH.boardOptions),

        // REAL:
        /** 사건 하나 — 대기 사건은 열람과 함께 검토 중이 된다 */
        get: (id: string): Promise<ReportCaseDetail> => client.get<ReportCaseDetail>(PATH.one(id)),

        // REAL:
        resolve: (id: string, body: ReportResolveRequest): Promise<ReportCaseDetail> => client.post<ReportCaseDetail>(PATH.act(id, 'resolve'), body),

        // REAL:
        dismiss: (id: string, body: ReportDismissRequest): Promise<ReportCaseDetail> => client.post<ReportCaseDetail>(PATH.act(id, 'dismiss'), body),

        // REAL:
        reopen: (id: string): Promise<ReportCaseDetail> => client.post<ReportCaseDetail>(PATH.act(id, 'reopen')),

        // REAL:
        /** 일괄 무시 — 사건마다 따로 처리하고 못 한 사건만 까닭과 함께 */
        bulkDismiss: (body: ReportBulkDismissRequest): Promise<ReportBulkResult> => client.post<ReportBulkResult>(PATH.bulkDismiss, body),
    };
}
