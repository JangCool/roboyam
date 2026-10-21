import {
    mockMyNotificationEventList,
    mockMyNotificationEventMute,
    mockNotificationEventList,
    mockNotificationEventUpdate,
} from '~/composables/notification/api/mock/notification-event-mock';
import type {
    MyNotificationEventMuteRequest,
    MyNotificationEventView,
    NotificationEventUpdateRequest,
    NotificationEventView,
} from '~/composables/notification/api/useNotificationEventApi.types';

/**
 * 알림 이벤트 API — 서버 주소 문자열(`/api/admin/notification-events…` · `/api/admin/profile/notification/events…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 이벤트의 정의는 서버 코드가 원천이고(수신 규칙은 코드에 고정 — 화면에서 바꾸지 않는다), 이 API 는 이벤트별 사용 여부·이메일 전달(운영)과 내가 끈 이벤트(개인)만 다룬다.
 *
 * 설계 확정 — 계약서 참조(`docs/features/notification-contract.md` 「API」). 오류는 모두 `ApiError`(useApiClient)로 던져진다. 쓰기는 CSRF.
 *   이벤트 관리(권한 `notification.read` / `notification.manage`)
 *   - `GET /api/admin/notification-events` → 200 `NotificationEventView[]` (영역·코드순. 이름·설명·수신 대상 문구는 서버가 요청 언어로 푼다)
 *   - `PUT /api/admin/notification-events/{code}` `{ enabled, emailEnabled }` → 200 바뀐 한 줄.
 *       409 `NOTIFICATION_MANDATORY`(필수 이벤트를 끔) · 409 `NOTIFICATION_EVENT_NOT_READY`(준비 중 이벤트) · 400 `VALIDATION` `fields.emailEnabled`(`emailable=false` 인데 이메일을 켬) · 404 `NOTIFICATION_EVENT_NOT_FOUND`. 감사 `NOTIFICATION_EVENT_UPDATE`(전·후 값)
 *   운영자 본인(로그인만 필요)
 *   - `GET /api/admin/profile/notification/events` → 200 `MyNotificationEventView[]` (내가 볼 수 있는 이벤트만, 준비 중 제외)
 *   - `PUT /api/admin/profile/notification/events/{code}` `{ muted }` → 200 바뀐 한 줄 · 409 `NOTIFICATION_MANDATORY`(필수 이벤트를 끔)
 *   (이메일 수신 설정 `GET·PUT /api/admin/profile/notification` `{ emailEnabled }` 은 `useProfileApi` 가 갖고 있다 — 같은 자원이 아니다.)
 *
 * ⚠ MOCK: 지금은 서버가 없어서 `./mock/notification-event-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄(과 `t`·`can`)을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    events: '/api/admin/notification-events',
    event: (code: string) => `${PATH.events}/${encodeURIComponent(code)}`,
    myEvents: '/api/admin/profile/notification/events',
    myEvent: (code: string) => `${PATH.myEvents}/${encodeURIComponent(code)}`,
};

export function useNotificationEventApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 목업이 서버처럼 이름·설명을 지금 언어로 풀고(`t`), 내가 볼 수 있는 이벤트를 내 권한으로 거른다(`can`)
    const t = useTranslate();
    const { can } = useAdminPermission();
    void PATH;

    return {
        /** 이벤트 목록 — 영역·코드순 */
        list: (): Promise<NotificationEventView[]> =>
            // MOCK:
            mockNotificationEventList(t),
        // REAL: client.get<NotificationEventView[]>(PATH.events),

        /** 이벤트 하나의 사용 여부·이메일 전달을 바꾼다. 바뀐 한 줄을 돌려받는다 */
        update: (code: string, body: NotificationEventUpdateRequest): Promise<NotificationEventView> =>
            // MOCK:
            mockNotificationEventUpdate(t, code, body),
        // REAL: client.put<NotificationEventView>(PATH.event(code), body),

        /** 내가 볼 수 있는 이벤트 목록(끈 것 표시 포함) */
        myEvents: (): Promise<MyNotificationEventView[]> =>
            // MOCK:
            mockMyNotificationEventList(t, can),
        // REAL: client.get<MyNotificationEventView[]>(PATH.myEvents),

        /** 이벤트 하나를 끄거나 다시 켠다. 바뀐 한 줄을 돌려받는다 */
        setMuted: (code: string, muted: boolean): Promise<MyNotificationEventView> =>
            // MOCK:
            mockMyNotificationEventMute(t, code, muted),
        // REAL: client.put<MyNotificationEventView>(PATH.myEvent(code), { muted } satisfies MyNotificationEventMuteRequest),
    };
}
