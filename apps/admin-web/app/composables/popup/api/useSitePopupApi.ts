import type { SitePopupItem, SitePopupListView, SitePopupSaveRequest } from '~/composables/popup/api/useSitePopupApi.types';

/**
 * 팝업 관리 API — 서버 주소(`/api/admin/site-popups…`)가 있는 **유일한 곳**이다. 상태는 없다. 계약: `docs/features/site-popup-contract.md`.
 * 그림 바이트 올리기는 하이라이트와 같은 두 걸음(파일 API `purpose=SITE_POPUP_IMAGE` → 받은 `fileId` 를 저장 본문 `imageFileId` 로).
 * 권한: 보기 `site.read`, 바꾸기 `site.manage`(공지 띠와 같다).
 *   - `GET /api/admin/site-popups` → `{ items, total, showingCount }`
 *   - `POST` → 201 한 줄 · `PUT /{id}` → 200 한 줄(모든 칸) · 400 `fields.name|imageFileId|imageAlt|link|heading|body|buttonLabel|devices|showUntil` · 404 `SITE_POPUP_NOT_FOUND`
 *   - `PUT /order { ids }` → 목록(전체 ID 의 새 차례, 집합이 다르면 400 `fields.ids`) · `PUT /{id}/visible { visible }` → 200 한 줄 · `DELETE /{id}` → 204
 */
const PATH = {
    root: '/api/admin/site-popups',
    order: '/api/admin/site-popups/order',
    one: (id: string) => `/api/admin/site-popups/${encodeURIComponent(id)}`,
    visible: (id: string) => `/api/admin/site-popups/${encodeURIComponent(id)}/visible`,
};

export function useSitePopupApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });
    const files = useFileApi();

    return {
        /** 전체 목록(공개 사이트에서 넘기는 차례) */
        list: (): Promise<SitePopupListView> => client.get<SitePopupListView>(PATH.root),

        create: (body: SitePopupSaveRequest): Promise<SitePopupItem> => client.post<SitePopupItem>(PATH.root, body),

        update: (id: string, body: SitePopupSaveRequest): Promise<SitePopupItem> => client.put<SitePopupItem>(PATH.one(id), body),

        /** 노출 켜기·끄기만 */
        setVisible: (id: string, visible: boolean): Promise<SitePopupItem> => client.put<SitePopupItem>(PATH.visible(id), { visible }),

        /** 전체 ID 의 새 차례 */
        reorder: (ids: string[]): Promise<SitePopupListView> => client.put<SitePopupListView>(PATH.order, { ids }),

        remove: (id: string): Promise<void> => client.del<void>(PATH.one(id)),

        /** 팝업 그림 올리기 — 받은 파일 ID 를 저장 본문에 넣어야 쓰인다 */
        uploadImage: async (file: File): Promise<string> => (await files.upload(file, 'SITE_POPUP_IMAGE')).fileId,
    };
}
