import { mockNotificationUnreadCount, mockRecentNotifications } from '~/composables/notification/api/mock/notification-mock';
import type { MockNotification } from '~/composables/notification/api/useNotificationApi.types';

/**
 * 알림 API — 서버 주소 문자열(`/api/notifications/…`)이 있는 **유일한 곳**이다 (frontend-guide 2장). 상태는 없다(안 읽은 수의 상태는 `useNotificationBadge`).
 *
 * ⚠ 단순 버전 — 원본(community)은 알림 목록·읽음 처리·설정 등 알림 화면 전체의 메서드를 가진다. 셸(종 배지·알림 패널)이 쓰는 안 읽은 수(`unreadCount`)와
 * 패널의 최근 알림(`recent`)만 남겼다. `recent` 의 모양(`MockNotification`)은 임시다 — 알림 화면을 만들 때 원본의 `NotificationItem` 으로 바꾼다.
 * ⚠ MOCK: 지금은 서버가 없어서 `./mock/notification-mock.ts` 의 목업을 부른다. 서버가 붙으면 `// MOCK:` 줄을 `// REAL:` 줄로 바꾼다.
 */
const PATH = {
    unreadCount: '/api/notifications/unread-count',
    list: '/api/notifications',
};

export function useNotificationApi() {
    // REAL: const client = useApiClient({ refreshCsrf: () => useAuthApi().me() });
    void PATH;

    return {
        /** 안 읽은 알림 수(서버 상한 100) */
        unreadCount: (): Promise<{ count: number }> =>
            // MOCK:
            mockNotificationUnreadCount(),
        // REAL: client.get<{ count: number }>(PATH.unreadCount),

        /** 알림 패널에 보일 최근 알림 */
        recent: (): Promise<MockNotification[]> =>
            // MOCK:
            mockRecentNotifications(),
        // REAL: client.get<…>(PATH.list, { query: { size: 8 } }),
    };
}
