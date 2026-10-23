import type {
    MemberNotificationFilter,
    MemberNotificationItem,
    MemberNotificationPage,
    MemberNotificationQuery,
    ReadAllResponse,
    UnreadCountResponse,
} from '~/composables/notification/api/useNotificationApi.types';

/**
 * 회원 알림 API — 서버 주소 문자열(`/api/notifications/…`)이 있는 **유일한 곳**이다. 상태는 없다(안 읽은 수의 상태는 `useNotificationBadge`).
 * 계약: `docs/features/member-notification-contract.md`. 로그인 필요(401), 내 알림만, 쓰기는 CSRF.
 *
 *  - `GET  /api/notifications?filter=all|comment|system&size=1~50&cursor=` → `MemberNotificationPage`
 *  - `GET  /api/notifications/unread-count` → `{ count }`(상한 100)
 *  - `POST /api/notifications/{id}/read` → 204(이미 읽음도 204). 남의·없는 ID `404 NOTIFICATION_NOT_FOUND`
 *  - `POST /api/notifications/read-all[?filter=]` → `{ updated }`
 * 오류: `400 VALIDATION` `fields.filter|size|cursor`.
 *
 * 셸(종 배지·알림 패널)과 알림 화면(`/notifications` — 목록·거르기·더 보기)이 쓴다. 서버의 `filter` 는 묶음(all·comment·system)뿐이고 「안 읽은 것만」 거르기는 없다.
 */
const PATH = {
    list: '/api/notifications',
    unreadCount: '/api/notifications/unread-count',
    read: (id: string) => `/api/notifications/${encodeURIComponent(id)}/read`,
    readAll: '/api/notifications/read-all',
};

/** 알림 패널에 보일 최근 알림 수 */
const RECENT_SIZE = 8;

export function useNotificationApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 안 읽은 알림 수(서버 상한 100) */
        unreadCount: (): Promise<UnreadCountResponse> => client.get<UnreadCountResponse>(PATH.unreadCount),

        /** 내 알림 한 쪽 */
        list: (query: MemberNotificationQuery = {}): Promise<MemberNotificationPage> => client.get<MemberNotificationPage>(PATH.list, { query: { ...query } }),

        /** 알림 패널에 보일 최근 알림(`size=8` 의 `items`) */
        recent: (): Promise<MemberNotificationItem[]> => client.get<MemberNotificationPage>(PATH.list, { query: { size: RECENT_SIZE } }).then((page) => page.items),

        /** 알림 하나를 읽음으로 — 이미 읽었어도 성공(204) */
        markRead: (id: string): Promise<void> => client.post<void>(PATH.read(id)),

        /** 모두 읽음 — `filter` 를 주면 그 묶음(`comment`·`system`)의 알림만, 없거나 `all` 이면 전부 */
        markAllRead: (filter?: MemberNotificationFilter): Promise<ReadAllResponse> =>
            client.post<ReadAllResponse>(PATH.readAll, undefined, { query: { filter: filter && filter !== 'all' ? filter : undefined } }),
    };
}
