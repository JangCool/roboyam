import type { SanctionCreateRequest, SanctionLine, SanctionListView, SanctionPreview, SanctionQuery, SanctionTarget } from '~/composables/sanction/api/useSanctionApi.types';

/**
 * 관리자 제재 API — 서버 주소(`/api/admin/sanctions…`)가 있는 **유일한 곳**이다. 상태는 없다. 계약 `docs/features/member-sanction-contract.md`(서버 `AdminSanctionController`).
 *   - `GET  /api/admin/sanctions?scope=&kind=&searchField=&q=&from=&to=&userId=&sort=&page=&size=` (`sanction.read`) → `SanctionListView` · 400 `fields.q|from|to`
 *       · 범위 `ACTIVE` 는 끝이 지난(만료 배치 전) 정지를 빼고, 끝이 지난 정지는 `ENDED`(`endedAt` = 끝)로 보인다(관리자 읽기는 지연 만료를 쓰지 않는다)
 *   - `GET  /{id}` (`sanction.read`) → `SanctionLine` · 404 `SANCTION_NOT_FOUND`
 *   - `GET  /targets?q=` (`sanction.read`) → `SanctionTarget[]` — 닉네임 2자 이상(400 `fields.q`), 최대 10명, 탈퇴 회원 제외
 *   - `GET  /preview?userId=` (`sanction.read`) → `SanctionPreview` · 404 `SANCTION_TARGET_NOT_FOUND`(없음·탈퇴)
 *   - `POST /` `SanctionCreateRequest` (입구 `sanction.manage` 또는 `sanction.ban` — 경고·기간 정지는 `sanction.manage`, 영구 정지·건너뛰기는 `sanction.ban`) → `201 SanctionLine` · 오류는 `SanctionCreateRequest` 주석
 *   - `POST /{id}/lift { reason }` (기간 정지 `sanction.manage` · 영구 정지 `sanction.ban`) → `200 SanctionLine` · 409 `SANCTION_NOT_LIFTABLE` · 404 `SANCTION_NOT_FOUND`
 * 권한이 모자라면 403 `PERMISSION_DENIED`. 쓰기는 CSRF(`useApiClient` 가 붙인다). 응답은 `Cache-Control: no-store`.
 */
const ROOT = '/api/admin/sanctions';
const PATH = {
    list: ROOT,
    targets: `${ROOT}/targets`,
    preview: `${ROOT}/preview`,
    one: (id: string) => `${ROOT}/${encodeURIComponent(id)}`,
    lift: (id: string) => `${ROOT}/${encodeURIComponent(id)}/lift`,
};

export function useSanctionApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 제재 목록 — 빈 조건은 보내지 않는다 */
        list: (query: SanctionQuery, signal?: AbortSignal): Promise<SanctionListView> =>
            client.get<SanctionListView>(PATH.list, {
                query: {
                    scope: query.scope && query.scope !== 'ALL' ? query.scope : undefined,
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

        /** 제재 하나 */
        get: (id: string): Promise<SanctionLine> => client.get<SanctionLine>(PATH.one(id)),

        /** 부여 대화창의 회원 찾기 */
        targets: (q: string): Promise<SanctionTarget[]> => client.get<SanctionTarget[]>(PATH.targets, { query: { q } }),

        /** 회원 하나의 제재 현황(경고 수·다음 단계·선택지·진행 중·이력) */
        preview: (userId: string): Promise<SanctionPreview> => client.get<SanctionPreview>(PATH.preview, { query: { userId } }),

        /** 제재 부여 */
        create: (body: SanctionCreateRequest): Promise<SanctionLine> => client.post<SanctionLine>(PATH.list, body),

        /** 진행 중 정지·영구 정지 해제 */
        lift: (id: string, reason: string): Promise<SanctionLine> => client.post<SanctionLine>(PATH.lift(id), { reason }),
    };
}
