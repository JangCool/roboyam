import { mockMyNotificationSettings, mockSaveMyNotificationSettings } from '~/composables/notification/api/mock/notification-settings-mock';
import type { MyNotificationSettings, MyNotificationSettingsRequest } from '~/composables/notification/api/useNotificationSettingsApi.types';

/**
 * 내 알림 설정 API(회원 이벤트별 받기 끄기) — 서버 주소 문자열(`/api/me/notification-settings`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 후보: `docs/features/member-mypage-contract.md` 「알림 설정」. 로그인 필요(401), 본인 것만, `PUT` 은 CSRF.
 *
 *  - `GET /api/me/notification-settings` → `MyNotificationSettings`(준비 중이 아닌 회원 이벤트 + 내 선택 + 이메일을 받을 수 있는지)
 *  - `PUT /api/me/notification-settings` `{ events: [{ code, inApp, email }] }`(회원이 끌 수 있는 이벤트만) → `MyNotificationSettings`.
 *    오류: `400 VALIDATION` `fields.events[i].code|inApp|email` · `409 NOTIFICATION_SETTING_LOCKED`(잠긴 이벤트를 끄려 함).
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/notification-settings-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    settings: '/api/me/notification-settings',
};

export function useNotificationSettingsApi() {
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH;

    return {
        /** 내 알림 설정 */
        get: (): Promise<MyNotificationSettings> =>
            // MOCK:
            mockMyNotificationSettings(),
        // REAL: client.get<MyNotificationSettings>(PATH.settings),

        /** 내 알림 설정 저장(끌 수 있는 이벤트 전부를 한 번에) */
        save: (body: MyNotificationSettingsRequest): Promise<MyNotificationSettings> =>
            // MOCK:
            mockSaveMyNotificationSettings(body),
        // REAL: client.put<MyNotificationSettings>(PATH.settings, body),
    };
}
