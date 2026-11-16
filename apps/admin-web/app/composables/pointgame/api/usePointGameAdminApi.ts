import type { PointGameSettingsSaveRequest, PointGameSettingsView } from '~/composables/pointgame/api/usePointGameAdminApi.types';
// MOCK:
import PointGameAdminMock from '~/composables/pointgame/api/mock/point-game-mock';

/**
 * 포인트 게임 관리자 API — 서버 주소 문자열(`/api/admin/point-games…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * ⚠ 목업(화면 확정 전, 2026-11-16) — `// MOCK:` 줄이 메모리 목업(`mock/point-game-mock.ts`)을 부르고 그 아래 `// REAL:` 줄이 후보 주소다. 서버가 생기면 MOCK 줄과 목업 import 만 지운다.
 * 권한 후보: 조회는 포인트 상점 설정 화면과 같은 `pointshop.read`, 저장 `pointshop.manage`(서버 `Permission` 에 따로 둘지는 미확정).
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
    // MOCK: 마지막 저장한 사람 — 서버는 세션으로 안다
    const { user } = useAdminAuth();
    // MOCK:
    const mockBy = () => user.value?.nickname ?? '—';

    return {
        /** 당첨 확인 링크 설정 */
        async settings(): Promise<PointGameSettingsView> {
            // MOCK:
            if (mockBy()) return PointGameAdminMock.settings();
            // REAL:
            return client.get<PointGameSettingsView>(PATH.settings);
        },

        /** 두 칸을 한 번에 저장 */
        async saveSettings(body: PointGameSettingsSaveRequest): Promise<PointGameSettingsView> {
            // MOCK:
            if (mockBy()) return PointGameAdminMock.save(body, mockBy());
            // REAL:
            return client.put<PointGameSettingsView>(PATH.settings, body);
        },
    };
}
