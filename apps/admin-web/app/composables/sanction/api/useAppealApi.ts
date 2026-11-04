import type { AppealAnswerRequest, AppealDetail, AppealListView, AppealQuery, AppealSummary } from '~/composables/sanction/api/useAppealApi.types';

/**
 * 관리자 이의제기 API — 서버 주소(`/api/admin/appeals…`)가 있는 **유일한 곳**이다. 상태는 없다. 계약 `docs/features/member-sanction-contract.md`(서버 `AdminAppealController`).
 *   - `GET  /api/admin/appeals?status=&sort=&page=&size=` (`sanction.read`) → `AppealListView`(상태 생략 = 전체, 정렬 기본 `oldest`)
 *   - `GET  /summary` (`sanction.read`) → `{ pending }`
 *   - `GET  /{id}` (`sanction.read`) → `AppealDetail` · 404 `APPEAL_NOT_FOUND`
 *   - `POST /{id}/accept` · `POST /{id}/reject` `{ answer }` (`appeal.manage`) → `200 AppealDetail` · 409 `APPEAL_CLOSED` · 400 `fields.answer` · 403 `PERMISSION_DENIED`
 * 쓰기는 CSRF(`useApiClient` 가 붙인다).
 */
const ROOT = '/api/admin/appeals';
const PATH = {
    list: ROOT,
    summary: `${ROOT}/summary`,
    one: (id: string) => `${ROOT}/${encodeURIComponent(id)}`,
    act: (id: string, action: 'accept' | 'reject') => `${ROOT}/${encodeURIComponent(id)}/${action}`,
};

export function useAppealApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        list: (query: AppealQuery, signal?: AbortSignal): Promise<AppealListView> =>
            client.get<AppealListView>(PATH.list, {
                query: { status: query.status && query.status !== 'ALL' ? query.status : undefined, sort: query.sort, page: query.page, size: query.size },
                signal,
            }),

        summary: (): Promise<AppealSummary> => client.get<AppealSummary>(PATH.summary),

        get: (id: string): Promise<AppealDetail> => client.get<AppealDetail>(PATH.one(id)),

        /** 인용 — 제재가 무효가 되고 진행 중 정지는 즉시 풀린다 */
        accept: (id: string, body: AppealAnswerRequest): Promise<AppealDetail> => client.post<AppealDetail>(PATH.act(id, 'accept'), body),

        /** 기각 — 제재는 그대로 */
        reject: (id: string, body: AppealAnswerRequest): Promise<AppealDetail> => client.post<AppealDetail>(PATH.act(id, 'reject'), body),
    };
}
