import type { MyNotificationSettings, MyNotificationSettingsRequest } from '~/composables/notification/api/useNotificationSettingsApi.types';

/**
 * 내 알림 설정 API(회원 이벤트별 받기 끄기) — 서버 주소 문자열(`/api/me/notification-settings`)이 있는 **유일한 곳**이다. 상태는 없다.
 * **서버 연결됨**(server/api `MyNotificationSettingsController`). 계약: `docs/features/member-mypage-contract.md` 「알림 설정」. 로그인 필요(401), 본인 것만, `PUT` 은 CSRF.
 *
 *  - `GET /api/me/notification-settings` → `MyNotificationSettings`(준비 중이 아닌 회원 이벤트 + 내 선택 + 이메일을 받을 수 있는지)
 *  - `PUT /api/me/notification-settings` `{ events: [{ code, inApp, email }] }`(회원이 끌 수 있는 이벤트만, 줄마다 세 칸 모두) → `MyNotificationSettings`.
 *    보낸 줄만 바꾸고, 운영자가 보내지 않는 채널의 값은 서버가 바꾸지 않는다(잠긴 이벤트를 켬으로 보내면 무시).
 *    오류: `400 VALIDATION` `fields.events[i].code|inApp|email` · `409 NOTIFICATION_SETTING_LOCKED`(잠긴 이벤트를 끄려 함).
 */
const PATH = {
    settings: '/api/me/notification-settings',
};

export function useNotificationSettingsApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 알림 설정 */
        get: (): Promise<MyNotificationSettings> => client.get<MyNotificationSettings>(PATH.settings),

        /** 내 알림 설정 저장(끌 수 있는 이벤트 전부를 한 번에) */
        save: (body: MyNotificationSettingsRequest): Promise<MyNotificationSettings> => client.put<MyNotificationSettings>(PATH.settings, body),
    };
}
