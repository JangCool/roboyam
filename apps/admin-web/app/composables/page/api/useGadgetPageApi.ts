import type { HomeLayoutBody, HomeLayoutSaveRequest } from '~/composables/home/api/useHomeLayoutApi.types';
import type { GadgetPage, GadgetPageCreateRequest, GadgetPageLayoutView, GadgetPageListResponse, GadgetPageUpdateRequest } from '~/composables/page/api/useGadgetPageApi.types';

/**
 * 관리자 가젯 페이지 API — 서버 주소 문자열(`/api/admin/gadget-pages`)이 있는 **유일한 곳**이다. 상태는 없다. 모양은 `useGadgetPageApi.types.ts`.
 * 계약: `docs/features/gadget-page-contract.md` 「API」(서버 `GadgetPageController`, 2026-10-29).
 *   - `GET /api/admin/gadget-pages` → 200 `GadgetPageListResponse`  (`site.read`)
 *   - `POST /api/admin/gadget-pages` `GadgetPageCreateRequest` → 200 `GadgetPage`  (`site.manage`) — 기준 폴더의 게시판으로 처음 배치를 만든다 ·
 *       400 `fields.key|name|nameEn|description|folderId|pages`
 *   - `PUT /api/admin/gadget-pages/{id}` `GadgetPageUpdateRequest` → 200 `GadgetPage`  (`site.manage`) · 404 `GADGET_PAGE_NOT_FOUND` · 400 같은 칸
 *   - `DELETE /api/admin/gadget-pages/{id}` → 200  (`site.manage`) — 이 페이지를 가리키던 메뉴 항목은 지우고 폴더는 연결만 비운다
 *   - `GET /api/admin/gadget-pages/{id}/layouts/{theme}` → 200 `GadgetPageLayoutView`  (`site.read`) — 저장한 배치, 없으면 처음 배치(`updatedAt: null`)
 *   - `GET /api/admin/gadget-pages/{id}/initial` → 200 `HomeLayoutBody`  (`site.read`) — 처음 배치(「처음 배치로」, 저장하지 않는다)
 *   - `PUT /api/admin/gadget-pages/{id}/layouts/{theme}` `HomeLayoutSaveRequest` → 200 `GadgetPageLayoutView`  (`site.manage`) — 홈 배치 저장과 같은 검사·같은 `config` 규칙
 *       (대상 게시판 `FOLDER` 를 받는다)
 */
const PATH = {
    pages: '/api/admin/gadget-pages',
    page: (id: string) => `/api/admin/gadget-pages/${encodeURIComponent(id)}`,
    layout: (id: string, theme: string) => `/api/admin/gadget-pages/${encodeURIComponent(id)}/layouts/${encodeURIComponent(theme)}`,
    initial: (id: string) => `/api/admin/gadget-pages/${encodeURIComponent(id)}/initial`,
};

export function useGadgetPageApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 페이지 목록 */
        list: (): Promise<GadgetPageListResponse> => client.get<GadgetPageListResponse>(PATH.pages),

        /** 만들기 — 처음 배치는 서버가 기준 폴더의 게시판으로 만든다 */
        create: (body: GadgetPageCreateRequest): Promise<GadgetPage> => client.post<GadgetPage>(PATH.pages, body),

        /** 페이지 정보 고치기 */
        update: (id: string, body: GadgetPageUpdateRequest): Promise<GadgetPage> => client.put<GadgetPage>(PATH.page(id), body),

        /** 지우기 */
        remove: (id: string): Promise<void> => client.del<void>(PATH.page(id)),

        /** 테마 하나의 배치 */
        layout: (id: string, theme: string): Promise<GadgetPageLayoutView> => client.get<GadgetPageLayoutView>(PATH.layout(id, theme)),

        /** 처음 배치(저장하지 않는다) */
        initial: (id: string): Promise<HomeLayoutBody> => client.get<HomeLayoutBody>(PATH.initial(id)),

        /** 테마 하나의 배치 저장 */
        saveLayout: (id: string, theme: string, body: HomeLayoutSaveRequest): Promise<GadgetPageLayoutView> => client.put<GadgetPageLayoutView>(PATH.layout(id, theme), body),
    };
}
