import { mockSiteAffiliateCreate, mockSiteAffiliateDelete, mockSiteAffiliateList, mockSiteAffiliateUpdate } from '~/composables/hotdeal/api/mock/hotdeal-mock';
import type { SiteAffiliateListView, SiteAffiliateStore, SiteAffiliateStoreRequest } from '~/composables/hotdeal/api/useHotdealSiteAffiliateApi.types';

/**
 * 사이트 제휴 몰 API(관리자) — 서버 주소(`/api/admin/hotdeal-site-affiliates…`)가 있는 **유일한 곳**이다. 상태는 없다. **서버에 아직 없다(목업)**.
 * 핫딜 설정(`useHotdealSettingsApi`)과 따로 바로 저장한다(설정 화면의 떠 있는 저장 줄과 무관). 권한: 조회·추가·고치기·지우기 `hotdeal.manage`.
 *   - `GET    /api/admin/hotdeal-site-affiliates` → `SiteAffiliateListView`(도메인 차례)
 *   - `POST   /api/admin/hotdeal-site-affiliates` · `PUT /{id}` `SiteAffiliateStoreRequest` → `SiteAffiliateStore`
 *       400 `fields.domain`·`fields.programName`·`fields.template`·`fields.query` · 409 `HOTDEAL_SITE_AFFILIATE_DUPLICATE` + `fields.domain`
 *   - `DELETE /api/admin/hotdeal-site-affiliates/{id}` → 204 · `404 HOTDEAL_SITE_AFFILIATE_NOT_FOUND`
 * 감사 `HOTDEAL_SITE_AFFILIATE_CREATE`·`HOTDEAL_SITE_AFFILIATE_UPDATE`·`HOTDEAL_SITE_AFFILIATE_DELETE`.
 *
 * ⚠ MOCK: 모든 메서드가 `./mock/hotdeal-mock.ts` 를 부른다.
 */
const PATH = {
    root: '/api/admin/hotdeal-site-affiliates',
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
};

export function useHotdealSiteAffiliateApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH; // MOCK: 서버가 붙으면 이 줄을 지운다

    return {
        list: (): Promise<SiteAffiliateListView> =>
            // MOCK:
            mockSiteAffiliateList(),
        // REAL: client.get<SiteAffiliateListView>(PATH.root),

        create: (body: SiteAffiliateStoreRequest): Promise<SiteAffiliateStore> =>
            // MOCK:
            mockSiteAffiliateCreate(body),
        // REAL: client.post<SiteAffiliateStore>(PATH.root, body),

        /** 고치기 · 켬/끔(줄 전체를 보낸다) */
        update: (id: string, body: SiteAffiliateStoreRequest): Promise<SiteAffiliateStore> =>
            // MOCK:
            mockSiteAffiliateUpdate(id, body),
        // REAL: client.put<SiteAffiliateStore>(PATH.one(id), body),

        remove: (id: string): Promise<void> =>
            // MOCK:
            mockSiteAffiliateDelete(id),
        // REAL: client.delete<void>(PATH.one(id)),
    };
}
