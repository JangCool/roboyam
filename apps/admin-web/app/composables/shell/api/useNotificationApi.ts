import type { NotificationListQuery, NotificationListResponse, NotificationSummaryResponse, NotificationWatch } from '~/composables/shell/api/useNotificationApi.types';

/**
 * 관리자 알림 API — 서버 주소 문자열(`/api/admin/notifications…`)이 있는 **유일한 곳**이다. 상태는 없다(화면이 쓰는 상태는 `useNotification`).
 *
 * 계약(서버 `NotificationController`, `docs/features/notification-contract.md` 「API」): 로그인한 운영자 본인 것만 다룬다(권한 불필요). 쓰기는 CSRF. 오류는 모두 `ApiError`(useApiClient)로 던져진다.
 *   - `GET  /api/admin/notifications?cursor&size`  → 200 `{ items: NotificationItem[], nextCursor|null, unread }` 최근 것부터, `size` 기본 30(최대 100 — 밖이면 400 `fields.size`, `cursor` 가 숫자가 아니면 400 `fields.cursor`).
 *       권한이 없는 이벤트·내가 끈 이벤트는 서버가 목록·`unread`·`summary` 에서 모두 뺀다. 제목·본문은 서버가 요청 언어로 푼다
 *   - `GET  /api/admin/notifications/summary`      → 200 `{ unread, latestId|null }` 폴링용(가볍다)
 *   - `POST /api/admin/notifications/{id}/read`    → 204 (내 것만 — 남의 알림·없는 알림은 404 `NOTIFICATION_NOT_FOUND`, 이미 읽은 것은 204)
 *   - `POST /api/admin/notifications/read-all`     → 204
 * 도착 경로는 **폴링**이다(서버 푸시는 만들지 않는다 — 계약서 결정 2). `subscribe` 가 그 자리다: 서랍이 닫혀 있는 동안 `summary` 를 20초마다(탭이 다시 보이면 즉시) 묻고,
 * `latestId` 가 바뀌면 목록 첫 쪽을 받아 `useNotification` 에 넘긴다(토스트·OS 알림은 거기서 `receive` 가 맡는다).
 */
const PATH = {
    root: '/api/admin/notifications',
    summary: () => `${PATH.root}/summary`,
    read: (id: string) => `${PATH.root}/${encodeURIComponent(id)}/read`,
    readAll: () => `${PATH.root}/read-all`,
};

/** 폴링 주기(ms) — 계약서 결정 2 */
const NOTIFICATION_POLL_MS = 20_000;
/** 폴링이 새 알림을 찾았을 때 받는 목록 크기 — 한 쪽(서버 기본)과 같다 */
const POLL_PAGE_SIZE = 30;

export function useNotificationApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    /** 알림 목록 한 쪽 */
    function list(query: NotificationListQuery = {}): Promise<NotificationListResponse> {
        return client.get<NotificationListResponse>(PATH.root, { query: { cursor: query.cursor, size: query.size } });
    }

    /** 안 읽은 수·가장 최근 알림 id(폴링용) */
    function summary(): Promise<NotificationSummaryResponse> {
        return client.get<NotificationSummaryResponse>(PATH.summary());
    }

    /**
     * 새 알림 도착을 받기 시작한다 — 폴링. 끝내는 함수를 돌려준다.
     * 서랍이 열려 있으면(`watch.isPaused()`) 묻지 않는다. 요청이 실패해도 조용히 넘어가고 다음 주기에 다시 묻는다(세션이 끊긴 401 은 `useApiClient` 가 로그인 화면으로 보낸다).
     */
    function subscribe(watch: NotificationWatch): () => void {
        let busy = false;

        async function tick(): Promise<void> {
            if (busy || watch.isPaused()) return;
            busy = true;
            try {
                const latest = await summary();
                if (watch.isPaused()) return;
                watch.onSummary(latest);
                // 가장 최근 알림이 아는 것과 같으면 새 알림이 없다 — 목록은 받지 않는다
                if (latest.latestId === watch.knownLatestId()) return;
                const page = await list({ size: POLL_PAGE_SIZE });
                if (watch.isPaused()) return;
                watch.onNew(page, latest);
            } catch {
                // 다음 주기에 다시 묻는다
            } finally {
                busy = false;
            }
        }

        // 탭이 다시 보이면 주기를 기다리지 않고 바로 묻는다
        const onVisible = () => {
            if (document.visibilityState === 'visible') void tick();
        };
        document.addEventListener('visibilitychange', onVisible);
        const timer = setInterval(() => void tick(), NOTIFICATION_POLL_MS);

        return () => {
            clearInterval(timer);
            document.removeEventListener('visibilitychange', onVisible);
        };
    }

    return {
        list,
        summary,

        /** 알림 하나를 읽음으로 */
        read: (id: string): Promise<void> => client.post<void>(PATH.read(id)),

        /** 모두 읽음으로 */
        readAll: (): Promise<void> => client.post<void>(PATH.readAll()),

        subscribe,
    };
}
