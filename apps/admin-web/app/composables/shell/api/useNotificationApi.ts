import { mockNotificationList, mockNotificationRead, mockNotificationReadAll, mockNotificationSubscribe } from '~/composables/shell/api/mock/notification-mock';
import type { NotificationItem, NotificationListResponse } from '~/composables/shell/api/useNotificationApi.types';

/**
 * 관리자 알림 API — 서버 주소 문자열(`/api/admin/notifications…`)이 있는 **유일한 곳**이다. 상태는 없다(화면이 쓰는 상태는 `useNotification`).
 *
 * 후보 계약(확정 전):
 *   - `GET  /api/admin/notifications`            → 200 `{ items: NotificationItem[] }` 최근 것부터. 건수·기간(예: 최근 30일 100건)은 서버가 정한다
 *   - `POST /api/admin/notifications/{id}/read`  → 204
 *   - `POST /api/admin/notifications/read-all`   → 204
 *   새 알림이 도착하는 경로(폴링 주기·서버 푸시 등)는 아직 정해지지 않았다 — `subscribe` 가 그 자리다. 도착하면 같은 진입점(`useNotification().receive`)을 부른다.
 *   알림이 어떤 사건에서 생기는지(누구에게 어떤 알림이 가는지)는 아직 정해지지 않았다 — 화면은 목록·읽음만 안다.
 *
 * ⚠ MOCK: 지금은 서버가 없어서 `./mock/notification-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    root: '/api/admin/notifications',
    read: (id: string) => `${PATH.root}/${encodeURIComponent(id)}/read`,
    readAll: () => `${PATH.root}/read-all`,
};

export function useNotificationApi() {
    // REAL: const client = useApiClient();
    void PATH;

    return {
        /** 알림 목록 */
        list: (): Promise<NotificationListResponse> =>
            // MOCK:
            mockNotificationList(),
        // REAL: client.get<NotificationListResponse>(PATH.root),

        /** 알림 하나를 읽음으로 */
        read: (id: string): Promise<void> =>
            // MOCK:
            mockNotificationRead(id),
        // REAL: client.post<void>(PATH.read(id)),

        /** 모두 읽음으로 */
        readAll: (): Promise<void> =>
            // MOCK:
            mockNotificationReadAll(),
        // REAL: client.post<void>(PATH.readAll()),

        /**
         * 새 알림 도착을 받기 시작한다. 도착할 때마다 `onArrive` 를 부르고, 끝내는 함수를 돌려준다.
         * MOCK: 주소 스위치 `?mock=notification-new` 또는 콘솔 `__mockNotify()`. REAL: 서버 경로가 정해지면 여기서 연결한다(폴링/푸시).
         */
        subscribe: (onArrive: (items: NotificationItem[]) => void): (() => void) =>
            // MOCK:
            mockNotificationSubscribe(onArrive),
        // REAL: startArrivalWatch(onArrive),
    };
}
