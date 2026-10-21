import { mockDmUnreadCount } from '~/composables/notification/api/mock/notification-mock';

/**
 * 쪽지 API — 서버 주소 문자열(`/api/dm/…`)이 있는 **유일한 곳**이다 (frontend-guide 2장). 상태는 없다(안 읽은 대화 수의 상태는 `useDmBadge`).
 *
 * ⚠ 단순 버전 — 원본(community)은 대화 목록·방·보내기 등 쪽지 화면 전체의 메서드를 가진다. 셸(헤더 쪽지 아이콘·메뉴의 쪽지함 줄)이 쓰는 안 읽은 대화 수(`unreadCount`)만 남겼다.
 * ⚠ MOCK: 지금은 서버가 없어서 `./mock/notification-mock.ts` 의 목업을 부른다. 서버가 붙으면 `// MOCK:` 줄을 `// REAL:` 줄로 바꾼다.
 */
const PATH = {
    unreadCount: '/api/dm/unread-count',
};

export function useDmApi() {
    // REAL: const client = useApiClient({ refreshCsrf: () => useAuthApi().me() });
    void PATH;

    return {
        /** 안 읽은 쪽지가 있는 대화 수(서버 상한 100) */
        unreadCount: (): Promise<{ count: number }> =>
            // MOCK:
            mockDmUnreadCount(),
        // REAL: client.get<{ count: number }>(PATH.unreadCount),
    };
}
