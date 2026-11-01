import type { AffiliateMode } from '~/composables/hotdeal/api/useHotdealApi.types';
import type { HotdealAffiliateImpact, HotdealSettingsRequest, HotdealSettingsView } from '~/composables/hotdeal/api/useHotdealSettingsApi.types';

/**
 * 핫딜 설정 API(관리자) — 서버 주소(`/api/admin/hotdeal-settings`)가 있는 **유일한 곳**이다. 상태는 없다.
 * 권한: 조회 `hotdeal.manage`(메뉴 항목), 저장 `hotdeal.manage`. 400 `fields.<칸>`(범위). 감사 `HOTDEAL_SETTINGS_UPDATE`(바뀐 칸만).
 *   - `GET /api/admin/hotdeal-settings` · `PUT`(`HotdealSettingsRequest` 통째로) → `HotdealSettingsView`
 *   - `GET /api/admin/hotdeal-settings/affiliate-impact?mode=` → `HotdealAffiliateImpact`(제휴 방식을 바꿀 때 확인 창의 영향 글 수 — 저장하지 않는다)
 */
const PATH = {
    root: '/api/admin/hotdeal-settings',
    affiliateImpact: '/api/admin/hotdeal-settings/affiliate-impact',
};

export function useHotdealSettingsApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        get: (): Promise<HotdealSettingsView> => client.get<HotdealSettingsView>(PATH.root),

        save: (body: HotdealSettingsRequest): Promise<HotdealSettingsView> => client.put<HotdealSettingsView>(PATH.root, body),

        /** 제휴 방식을 `mode` 로 바꾸면 영향받는 진행 중 글 수 */
        affiliateImpact: (mode: AffiliateMode): Promise<HotdealAffiliateImpact> => client.get<HotdealAffiliateImpact>(PATH.affiliateImpact, { query: { mode } }),
    };
}
