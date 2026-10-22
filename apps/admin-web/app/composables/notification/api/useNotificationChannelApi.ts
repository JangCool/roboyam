import type {
    NotificationChannelEnabledRequest,
    NotificationChannelSaveRequest,
    NotificationChannelTestResult,
    NotificationChannelView,
} from '~/composables/notification/api/useNotificationChannelApi.types';

/**
 * 알림 도구(채널) API — 서버 주소 문자열(`/api/admin/notification-channels…`)이 있는 **유일한 곳**이다. 상태는 없다(화면이 쓰는 상태는 `useNotificationChannels`).
 * 알림을 보낼 **도구**: 서랍 · 이메일 · 브라우저 알림(기본 제공, 설정 칸 없음 — id 가 `drawer`·`email`·`browser`)과 사용자가 만드는 Slack · Mattermost. 이메일은 지금 방식 그대로 설정 > 메일 서버 설정으로 발송하고 도구 전용 SMTP 는 없다.
 * 카카오 · SMS 는 업체 계약 전이라 만들 수 없다. 이벤트마다 어느 도구로 보낼지는 `useNotificationEventApi` 의 전달 설정이다(`docs/features/notification-contract.md` 「알림 도구·조직 구성」).
 *
 * 계약(서버 `NotificationChannelController`). 오류는 모두 `ApiError`(useApiClient)로 던져진다. 쓰기는 CSRF. 권한: 읽기 `notification.read`, 쓰기·시험 `notification.manage`.
 *   - `GET    /api/admin/notification-channels` → 200 `NotificationChannelView[]` (기본 제공 → 만든 차례. **비밀 값은 응답에 없다** — `secretKeys` 로 「입력됨」만 알린다. `notification.read`)
 *   - `POST   /api/admin/notification-channels` `NotificationChannelSaveRequest` → 201 `NotificationChannelView` (Slack · Mattermost 만. 400 `VALIDATION` `fields.<칸>` · 400 `NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED`(카카오·SMS) `fields.type` ·
 *       409 `NOTIFICATION_CHANNEL_NAME_DUPLICATED` `fields.name` · 409 `NOTIFICATION_CHANNEL_BUILTIN`(기본 제공 종류) · 409 `NOTIFICATION_CHANNEL_LIMIT_EXCEEDED`(최대 20개) · 503 `CRYPTO_KEY_MISSING`(비밀을 저장할 키가 없는 서버). `notification.manage`)
 *   - `PUT    /api/admin/notification-channels/{channelId}` `NotificationChannelSaveRequest` → 200 `NotificationChannelView` (종류는 못 바꾼다(400 `fields.type`). 비밀은 **보낸 칸만** 바꾸고 안 보내면 저장된 값을 지킨다. 기본 제공 도구는 사용 여부만 바뀐다)
 *   - `PUT    /api/admin/notification-channels/{channelId}/enabled` `NotificationChannelEnabledRequest` → 200 `NotificationChannelView` (사용 켜기/끄기 — 서랍을 끄면 409 `NOTIFICATION_CHANNEL_ALWAYS_ON`)
 *   - `DELETE /api/admin/notification-channels/{channelId}` → 204 (기본 제공은 409 `NOTIFICATION_CHANNEL_BUILTIN`. 이 도구를 고른 이벤트에서는 함께 빠진다)
 *   - `POST   /api/admin/notification-channels/{channelId}/test` → 200 `NotificationChannelTestResult` (연결 시험. 시험 결과는 `lastTest` 로 도구에 남는다. 서랍·브라우저는 400 `NOTIFICATION_CHANNEL_NOT_TESTABLE`. 운영자당 분당 6회 — 넘으면 429 `NOTIFICATION_CHANNEL_TEST_RATE_LIMITED` + `Retry-After`.
 *       시험이 **실패한 것**은 오류가 아니라 `ok:false` 응답이다 — 요청 자체가 실패한 것(권한·한도·서버 오류)만 `ApiError`)
 *   공통 오류: 404 `NOTIFICATION_CHANNEL_NOT_FOUND` · 403 `PERMISSION_DENIED`. 감사: `NOTIFICATION_CHANNEL_CREATE|UPDATE|DELETE|TOGGLE|TEST`(비밀 값은 기록하지 않는다).
 *   비밀 저장: 서버가 암호문으로 저장한다(`ROBOYAM_CRYPTO_KEY`, 메일 비밀번호와 같은 방식). 화면은 비밀을 그 화면 메모리에만 두고 제출하면 비운다(`forms.md`).
 */
const PATH = {
    channels: '/api/admin/notification-channels',
    channel: (channelId: string) => `${PATH.channels}/${encodeURIComponent(channelId)}`,
    enabled: (channelId: string) => `${PATH.channel(channelId)}/enabled`,
    test: (channelId: string) => `${PATH.channel(channelId)}/test`,
};

export function useNotificationChannelApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 도구 목록 — 기본 제공 도구 먼저 */
        list: (): Promise<NotificationChannelView[]> => client.get<NotificationChannelView[]>(PATH.channels),

        /** 도구를 만든다(Slack · Mattermost) */
        create: (body: NotificationChannelSaveRequest): Promise<NotificationChannelView> => client.post<NotificationChannelView>(PATH.channels, body),

        /** 도구를 고친다 — 안 보낸 비밀은 저장된 값을 지킨다 */
        update: (channelId: string, body: NotificationChannelSaveRequest): Promise<NotificationChannelView> => client.put<NotificationChannelView>(PATH.channel(channelId), body),

        /** 사용 켜기/끄기 */
        setEnabled: (channelId: string, body: NotificationChannelEnabledRequest): Promise<NotificationChannelView> => client.put<NotificationChannelView>(PATH.enabled(channelId), body),

        /** 도구를 지운다 */
        remove: (channelId: string): Promise<void> => client.del<void>(PATH.channel(channelId)),

        /** 연결을 시험한다 — 실패한 시험도 200 `ok:false` 로 돌아온다 */
        test: (channelId: string): Promise<NotificationChannelTestResult> => client.post<NotificationChannelTestResult>(PATH.test(channelId)),
    };
}
