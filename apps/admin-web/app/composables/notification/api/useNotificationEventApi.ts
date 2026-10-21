import { mockChannelStates } from '~/composables/notification/api/mock/notification-channel-mock';
import { mockNotificationEventDeliveries, mockNotificationEventSetDelivery } from '~/composables/notification/api/mock/notification-event-delivery-mock';
import type {
    MyNotificationEventMuteRequest,
    MyNotificationEventView,
    NotificationEventDelivery,
    NotificationEventDeliveryRequest,
    NotificationEventUpdateRequest,
    NotificationEventView,
} from '~/composables/notification/api/useNotificationEventApi.types';
import { mockTeamStates } from '~/composables/team/api/mock/team-mock';

/**
 * 알림 이벤트 API — 서버 주소 문자열(`/api/admin/notification-events…` · `/api/admin/profile/notification/events…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 이벤트의 정의는 서버 코드가 원천이고(수신 규칙은 코드에 고정 — 화면에서 바꾸지 않는다), 이 API 는 이벤트별 사용 여부·이메일 전달(운영)과 내가 끈 이벤트(개인)만 다룬다.
 *
 * 계약(서버 `NotificationController`, `docs/features/notification-contract.md` 「API」). 오류는 모두 `ApiError`(useApiClient)로 던져진다. 쓰기는 CSRF.
 *   이벤트 관리(권한 `notification.read` / `notification.manage`)
 *   - `GET /api/admin/notification-events` → 200 `NotificationEventView[]` (영역·코드순, 22개. 이름·설명·수신 대상 문구는 서버가 요청 언어로 푼다)
 *   - `PUT /api/admin/notification-events/{code}` `{ enabled, emailEnabled }` → 200 바뀐 한 줄. 검사 순서:
 *       404 `NOTIFICATION_EVENT_NOT_FOUND` → 400 `VALIDATION` `fields.enabled|emailEnabled`(없음) → 409 `NOTIFICATION_EVENT_NOT_READY`(준비 중 이벤트) → 409 `NOTIFICATION_MANDATORY`(필수 이벤트를 끔)
 *       → 400 `VALIDATION` `fields.emailEnabled`(`emailable=false` 인데 이메일을 켬). 감사 `NOTIFICATION_EVENT_UPDATE`(전·후 값)
 *   운영자 본인(로그인만 필요)
 *   - `GET /api/admin/profile/notification/events` → 200 `MyNotificationEventView[]` (내가 볼 수 있는 이벤트만, 준비 중 제외. `emailable` = 코드가 이메일을 허용하고 이벤트 관리의 이메일 전달이 켜짐)
 *   - `PUT /api/admin/profile/notification/events/{code}` `{ muted }` → 200 바뀐 한 줄 · 409 `NOTIFICATION_MANDATORY`(필수 이벤트를 끔) · 404 `NOTIFICATION_EVENT_NOT_FOUND`(모르는·준비 중·권한 없는 이벤트) · 400 `fields.muted`(없음)
 *   (이메일 수신 설정 `GET·PUT /api/admin/profile/notification` `{ emailEnabled }` 은 `useProfileApi` 가 갖고 있다 — 같은 자원이 아니다.)
 *
 * ⚠ 전달 도구·추가 수신 팀(`deliveries`·`setDelivery`)만 **MOCK** 이다 — 나머지는 실제 서버. 후보 계약(확정 전, `docs/features/notification-contract.md` 「알림 도구·조직 구성」):
 *   - `GET /api/admin/notification-events` 의 각 줄에 `channels: string[]`(고른 전달 도구의 `channelId`, 이메일 도구 `email` 포함, 서랍 제외) · `teamIds: string[]`(추가 수신 팀) · `broadcastAllowed: boolean`(팀·공유 채널로 보낼 수 있는 이벤트인가)이 더해진다.
 *   - `PUT /api/admin/notification-events/{code}` 의 요청에 `channels` · `teamIds` 가 더해진다: `{ enabled, emailEnabled, channels, teamIds }` → 200 바뀐 한 줄. 서버가 `channels` 에 `email` 이 있는지를 `emailEnabled` 와 같은 값으로 본다(둘을 따로 두지 않는다).
 *       추가 오류: 400 `NOTIFICATION_BROADCAST_NOT_ALLOWED`(본인에게만 가는 이벤트에 팀·Slack·Mattermost) · 400 `NOTIFICATION_CHANNEL_NOT_FOUND` · 400 `TEAM_NOT_FOUND` · 409 `NOTIFICATION_CHANNEL_DISABLED`·`TEAM_DISABLED`(꺼진 도구·멈춘 팀을 새로 고름) · 400 `fields.channels`(`emailable=false` 인데 이메일 도구를 고름 — 지금의 `fields.emailEnabled`).
 *       감사 `NOTIFICATION_EVENT_UPDATE` 의 전·후 값에 `channels`·`teamIds` 가 더해진다.
 *   - 수신 의미: 코드에 고정된 기본 수신 규칙은 그대로이고, 고른 팀의 팀원(운영자)은 **서랍 · 이메일 · 브라우저 알림**으로 추가 수신한다(`requiredPermission` 이 없는 팀원은 걸러진다 — 알림 조회 규칙과 같다).
 *       Slack · Mattermost 도구는 팀원에게 따로 가지 않고 **도구에 설정한 채널로 한 번** 게시된다.
 *   목업 단계에서는 이메일만 지금의 `emailEnabled`(실제 서버)에 붙이고 나머지 도구·팀은 목업 저장소에 둔다 — 화면(`useNotificationEvents.changeDelivery`)이 둘을 합쳐 보인다.
 */
const PATH = {
    events: '/api/admin/notification-events',
    event: (code: string) => `${PATH.events}/${encodeURIComponent(code)}`,
    myEvents: '/api/admin/profile/notification/events',
    myEvent: (code: string) => `${PATH.myEvents}/${encodeURIComponent(code)}`,
};

export function useNotificationEventApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 이벤트 목록 — 영역·코드순 */
        list: (): Promise<NotificationEventView[]> => client.get<NotificationEventView[]>(PATH.events),

        /** 이벤트 하나의 사용 여부·이메일 전달을 바꾼다. 바뀐 한 줄을 돌려받는다 */
        update: (code: string, body: NotificationEventUpdateRequest): Promise<NotificationEventView> => client.put<NotificationEventView>(PATH.event(code), body),

        /** 모든 이벤트의 전달 도구·추가 수신 팀 — 이벤트 목록(`list`)과 함께 받아 코드로 합친다 */
        deliveries: (): Promise<NotificationEventDelivery[]> =>
            // MOCK:
            mockNotificationEventDeliveries(),
        // REAL: (서버가 `list()` 응답에 channels·teamIds·broadcastAllowed 를 실어 주면 이 메서드는 없어진다)

        /** 이벤트 하나의 전달 도구(이메일 제외)·추가 수신 팀을 바꾼다. 바뀐 한 줄을 돌려받는다 */
        setDelivery: (code: string, body: NotificationEventDeliveryRequest): Promise<NotificationEventDelivery> =>
            // MOCK:
            mockNotificationEventSetDelivery(code, body, { channels: mockChannelStates(), teams: mockTeamStates() }),
        // REAL: client.put<NotificationEventView>(PATH.event(code), { enabled, emailEnabled, channels, teamIds }) — `update` 의 요청 모양이 넓어진다

        /** 내가 볼 수 있는 이벤트 목록(끈 것 표시 포함) */
        myEvents: (): Promise<MyNotificationEventView[]> => client.get<MyNotificationEventView[]>(PATH.myEvents),

        /** 이벤트 하나를 끄거나 다시 켠다. 바뀐 한 줄을 돌려받는다 */
        setMuted: (code: string, muted: boolean): Promise<MyNotificationEventView> =>
            client.put<MyNotificationEventView>(PATH.myEvent(code), { muted } satisfies MyNotificationEventMuteRequest),
    };
}
