import type { PointGameSettingsSaveRequest, PointGameSettingsView } from '~/composables/pointgame/api/usePointGameAdminApi.types';

/**
 * 포인트 게임 관리자 API — 서버 주소 문자열(`/api/admin/point-games…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 서버 `PointGameAdminController`(server/admin). 권한은 포인트 상점과 같다 — 조회 `pointshop.read`, 저장 `pointshop.manage`. 저장은 감사(`POINT_GAME_SETTINGS_UPDATE`).
 *
 *  - `GET /api/admin/point-games/settings` → `PointGameSettingsView`
 *  - `PUT /api/admin/point-games/settings` → `PointGameSettingsView` — `400 VALIDATION fields.lotteryProviderName|lotteryCheckUrl`
 */
const PATH = {
    settings: '/api/admin/point-games/settings',
};

export function usePointGameAdminApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 당첨 확인 링크 설정 */
        settings(): Promise<PointGameSettingsView> {
            return client.get<PointGameSettingsView>(PATH.settings);
        },

        /** 두 칸을 한 번에 저장 */
        saveSettings(body: PointGameSettingsSaveRequest): Promise<PointGameSettingsView> {
            return client.put<PointGameSettingsView>(PATH.settings, { lotteryProviderName: body.lotteryProviderName, lotteryCheckUrl: body.lotteryCheckUrl });
        },
    };
}
