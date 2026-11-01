import { mockSettingsGet, mockSettingsSave } from '~/composables/hotdeal/api/mock/hotdeal-mock';
import type { HotdealSettingsRequest, HotdealSettingsView } from '~/composables/hotdeal/api/useHotdealSettingsApi.types';

/**
 * 핫딜 설정 API(관리자) — 서버 주소(`/api/admin/hotdeal-settings`)가 있는 **유일한 곳**이다. 상태는 없다. **서버에 아직 없다(목업)**.
 * 권한: 조회 `hotdeal.manage`(메뉴 항목), 저장 `hotdeal.manage`. 400 `fields.<칸>`(범위). 감사 `HOTDEAL_SETTINGS_UPDATE`(바뀐 칸만).
 *
 * ⚠ MOCK: 모든 메서드가 `./mock/hotdeal-mock.ts` 를 부른다.
 */
const PATH = {
    root: '/api/admin/hotdeal-settings',
};

export function useHotdealSettingsApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH; // MOCK: 서버가 붙으면 이 줄을 지운다

    return {
        get: (): Promise<HotdealSettingsView> =>
            // MOCK:
            mockSettingsGet(),
        // REAL: client.get<HotdealSettingsView>(PATH.root),

        save: (body: HotdealSettingsRequest): Promise<HotdealSettingsView> =>
            // MOCK:
            mockSettingsSave(body),
        // REAL: client.put<HotdealSettingsView>(PATH.root, body),
    };
}
