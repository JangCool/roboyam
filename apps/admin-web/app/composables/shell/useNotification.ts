import type { NotificationItem, NotificationKind, NotificationListResponse, NotificationSummaryResponse } from '~/composables/shell/api/useNotificationApi.types';
import type { NotificationToastDetail, OsNotificationPermission } from '~/composables/shell/useNotification.types';
import UtilAdminMenu from '~/utils/util-admin-menu';
import UtilNotification from '~/utils/util-notification';

/**
 * 관리자 알림 — 상단 종(안 읽은 수)과 알림 서랍이 **같은 상태**를 본다(`useState`).
 *
 * `items`: `undefined` = 아직 안 물어봄 · 배열 = 받았음(비었으면 알림 없음). 이미 받았으면 다시 묻지 않는다(`ensureLoaded`) — 서랍을 열 때 `reload` 로 새로 받는다.
 * `nextCursor`: 다음 쪽이 있으면 그 커서(서랍의 「더 보기」), 끝이면 null. `unread`: **서버가 센 안 읽은 수**(목록·요약 응답) — 불러온 목록만 세지 않는다(목록에 없는 것까지 센다).
 * 서버와 말하는 것은 `useNotificationApi` 다. 여기는 상태와 낙관적 갱신, 새 알림 도착 알림만 든다.
 *
 * ── 새 알림이 도착하면 (`receive`) ─ 화면 설정(`notifyToast` · `notifyOs`)을 따른다
 *   창을 보고 있다                              → 토스트(종 아래 오른쪽 위). 서랍이 열려 있으면 목록에 이미 떠오르므로 토스트를 또 얹지 않는다
 *   창이 가려졌다 · 다른 창에 초점이 있다         → OS 알림(권한이 허용됐을 때). 못 쓰면 토스트로 물러선다
 * 도착 경로는 폴링이다 — `useNotificationApi().subscribe`(`startWatching`)가 서랍이 닫힌 동안 20초마다 요약을 묻고, 최근 알림이 바뀌었으면 목록 첫 쪽을 `onPolled` 로 넘긴다.
 *
 * 항목을 누르면(`follow`) 읽음 처리 후 그 항목의 `link.path` 로 간다. 운영자가 그 화면을 열 권한이 없으면(`UtilAdminMenu.canOpen`) 이동하지 않고 읽음만 처리한다 — 권한 판정은 메뉴 정의가 이미 갖고 있어 그것을 쓴다.
 */

/** 새 알림 토스트 묶음 이름 — 셸(`layouts/default.vue`)이 이 묶음에 「알림 이력 보기」 단추가 있는 틀을 건다 */
export const NOTIFICATION_TOAST_GROUP = 'admin-notification';

/** 토스트가 스스로 꺼지는 시간(ms). 놓쳐도 종의 숫자와 서랍에 남는다 */
const TOAST_LIFE_MS = 6000;
/** 토스트·OS 알림에 한꺼번에 적는 제목 줄 수 */
const ANNOUNCE_MAX = 3;
/** 서랍이 한 번에 받는 알림 수(서버 기본과 같다) */
const PAGE_SIZE = 30;

const TOAST_SEVERITY: Record<NotificationKind, 'info' | 'success' | 'warn' | 'error'> = {
    info: 'info',
    success: 'success',
    warning: 'warn',
    critical: 'error',
};

/** 토스트 색을 정할 때 더 눈에 띄어야 하는 순서(앞이 우선) */
const SEVERITY_ORDER: NotificationKind[] = ['critical', 'warning', 'success', 'info'];

function currentPermission(): OsNotificationPermission {
    if (!import.meta.client || !('Notification' in window)) return 'unsupported';
    return Notification.permission;
}

export function useNotification() {
    const t = useTranslate();
    const api = useNotificationApi();
    const appToast = useAppToast();
    // ⚠ 도착 알림·항목 누름은 비동기 콜백에서 오므로 토스트 서비스·라우터·권한·화면 설정·사이트 이름은 setup 때 붙잡아 둔다 (composables.md Nuxt 문맥 함정)
    const toast = useToast();
    const router = useRouter();
    const { can } = useAdminPermission();
    const { prefs } = useLayoutPrefs();
    const { siteLabel } = useAdminAuth();

    const items = useState<NotificationItem[] | undefined>('admin-notifications', () => undefined);
    const nextCursor = useState<string | null>('admin-notification-cursor', () => null);
    /** 서버가 센 안 읽은 수 — 종 뱃지와 서랍 머리글이 같은 값을 본다 */
    const unread = useState<number>('admin-notification-unread', () => 0);
    /** 지금 아는 가장 최근 알림 id — 폴링이 요약의 `latestId` 와 견줘 새 알림을 알아본다. `undefined` = 목록을 아직 못 받았다 */
    const latestId = useState<string | null | undefined>('admin-notification-latest', () => undefined);
    /** 서랍이 열려 있는가 — 종 · 토스트의 「알림 이력 보기」 · OS 알림 누름이 같은 서랍을 연다 */
    const open = useState<boolean>('admin-notification-open', () => false);
    const loading = useState<boolean>('admin-notification-loading', () => false);
    const loadingMore = useState<boolean>('admin-notification-loading-more', () => false);
    const failed = useState<boolean>('admin-notification-failed', () => false);
    const osPermission = useState<OsNotificationPermission>('admin-notification-os', currentPermission);

    /** 목록 응답을 상태에 반영한다(첫 쪽을 통째로 바꾼다) */
    function applyFirstPage(page: NotificationListResponse): void {
        items.value = page.items;
        nextCursor.value = page.nextCursor;
        unread.value = page.unread;
        latestId.value = page.items[0]?.id ?? null;
    }

    async function reload(): Promise<void> {
        if (loading.value) return;
        loading.value = true;
        failed.value = false;
        try {
            applyFirstPage(await api.list({ size: PAGE_SIZE }));
        } catch {
            failed.value = true;
        } finally {
            loading.value = false;
        }
    }

    /** 아직 안 받았을 때만 받는다 */
    async function ensureLoaded(): Promise<void> {
        if (items.value === undefined) await reload();
    }

    /** 「더 보기」 — 다음 쪽을 받아 목록 끝에 붙인다. 실패하면 토스트(이미 받은 목록은 그대로 둔다) */
    async function loadMore(): Promise<void> {
        const cursor = nextCursor.value;
        if (!cursor || loadingMore.value || !items.value) return;
        loadingMore.value = true;
        try {
            const page = await api.list({ cursor, size: PAGE_SIZE });
            const known = new Set(items.value.map((item) => item.id));
            items.value = [...items.value, ...page.items.filter((item) => !known.has(item.id))];
            nextCursor.value = page.nextCursor;
            unread.value = page.unread;
        } catch {
            appToast.error(t('notification.loadMoreFailed'));
        } finally {
            loadingMore.value = false;
        }
    }

    /** 알림 하나를 읽음으로 — 화면을 먼저 바꾸고 서버가 실패하면 되돌린다 */
    async function markRead(id: string): Promise<void> {
        const before = items.value;
        const beforeUnread = unread.value;
        if (!before?.some((item) => item.id === id && !item.read)) return;
        items.value = before.map((item) => (item.id === id ? { ...item, read: true } : item));
        unread.value = Math.max(0, beforeUnread - 1);
        try {
            await api.read(id);
        } catch {
            items.value = before;
            unread.value = beforeUnread;
            appToast.error(t('notification.readFailed'));
        }
    }

    /** 모두 읽음으로 — 화면을 먼저 바꾸고 서버가 실패하면 되돌린다 */
    async function markAllRead(): Promise<void> {
        const before = items.value;
        const beforeUnread = unread.value;
        if (!before || beforeUnread === 0) return;
        items.value = before.map((item) => ({ ...item, read: true }));
        unread.value = 0;
        try {
            await api.readAll();
        } catch {
            items.value = before;
            unread.value = beforeUnread;
            appToast.error(t('notification.readFailed'));
        }
    }

    /**
     * 항목을 눌렀다 — 읽음 처리하고, 이동 경로가 있으면 그 화면으로 간다(서랍은 닫는다).
     * 운영자가 그 화면을 열 권한이 없으면 이동하지 않고 읽음만 처리한다(막는 것은 서버, 여기는 편의 — 열어도 셸이 「권한 없음」을 보일 화면으로 보내지 않으려는 것이다).
     */
    async function follow(item: NotificationItem): Promise<void> {
        const reading = markRead(item.id);
        const path = item.link?.path;
        if (path && UtilAdminMenu.canOpen(UtilNotification.pathOf(path), can)) {
            open.value = false;
            await router.push(path);
        }
        await reading;
    }

    // ── OS 알림 권한 ──────────────────────────────────────────────────────────────

    /** 사용자가 누른 자리에서 OS 알림 권한을 묻는다 (브라우저는 조작 없이 묻는 것을 막는다) */
    async function requestOsPermission(): Promise<OsNotificationPermission> {
        if (currentPermission() === 'unsupported') return (osPermission.value = 'unsupported');
        try {
            osPermission.value = await Notification.requestPermission();
        } catch {
            osPermission.value = currentPermission();
        }
        return osPermission.value;
    }

    /** 브라우저 설정에서 바뀌었을 수 있다 — 설정 서랍을 열 때 다시 읽는다 */
    function refreshOsPermission(): void {
        osPermission.value = currentPermission();
    }

    // ── 새 알림 도착 ──────────────────────────────────────────────────────────────

    function showToast(arrived: NotificationItem[]): void {
        const shown = arrived.slice(0, ANNOUNCE_MAX);
        const detail: NotificationToastDetail = {
            lines: shown.map((item) => item.title),
            summary: arrived.length === 1 ? arrived[0]!.body : null,
            more: arrived.length > shown.length ? t('notification.toastMore', { n: arrived.length - shown.length }) : null,
        };
        // 가장 눈에 띄어야 하는 종류(긴급 → 경고 → 성공 → 안내)로 색을 정한다
        const worst = SEVERITY_ORDER.find((kind) => arrived.some((item) => item.kind === kind)) ?? 'info';
        toast.add({
            group: NOTIFICATION_TOAST_GROUP,
            severity: TOAST_SEVERITY[worst],
            summary: t('notification.toastTitle', { n: arrived.length }),
            detail,
            life: TOAST_LIFE_MS,
        });
    }

    function showOs(arrived: NotificationItem[]): void {
        const first = arrived[0]!;
        const body = [first.title, first.body ?? '', arrived.length > 1 ? t('notification.toastMore', { n: arrived.length - 1 }) : ''].filter(Boolean);
        try {
            const note = new Notification(t('notification.osTitle', { product: siteLabel.value, n: arrived.length }), { body: body.join('\n') });
            // 누르면 창을 앞으로 가져오고 알림 서랍을 연다
            note.onclick = () => {
                window.focus();
                open.value = true;
                note.close();
            };
        } catch {
            // 일부 브라우저(안드로이드 크롬)는 페이지에서 `new Notification` 을 막는다 — 토스트로 물러선다
            if (prefs.value.notifyToast) showToast(arrived);
        }
    }

    /**
     * 새 알림이 도착했다 — 목록에 더하고 설정대로 알린다. 새 알림을 알리는 유일한 진입점이다(폴링의 `onPolled` 가 부른다).
     * 목록을 아직 안 받았으면(`undefined`) 더하지 않는다 — 받을 때 서버가 이 알림까지 준다.
     */
    function receive(arrived: NotificationItem[]): void {
        const known = new Set(items.value?.map((item) => item.id));
        const fresh = arrived.filter((item) => !known.has(item.id));
        if (!fresh.length) return;
        if (items.value) {
            items.value = [...fresh, ...items.value];
            unread.value += fresh.filter((item) => !item.read).length;
            latestId.value = items.value[0]?.id ?? latestId.value;
        }

        // 「보고 있나」 — 창이 보이는 것만으로는 모자라다. 옆 모니터에서 일하면 창은 보여도 눈은 거기 없다
        const away = document.visibilityState === 'hidden' || !document.hasFocus();
        refreshOsPermission();
        if (away && prefs.value.notifyOs && osPermission.value === 'granted') {
            showOs(fresh);
            return;
        }
        // 서랍을 보고 있으면 그 목록에 이미 떠오른다 — 토스트를 또 얹지 않는다
        if (!away && open.value) return;
        if (prefs.value.notifyToast) showToast(fresh);
    }

    /** 폴링이 새 알림을 찾았다 — 목록 첫 쪽에서 아직 모르는 안 읽은 알림만 알리고, 안 읽은 수는 서버 값으로 맞춘다 */
    function onPolled(page: NotificationListResponse, summary: NotificationSummaryResponse): void {
        if (items.value === undefined) {
            // 목록을 처음 받는 것 — 알림 없이 상태만 채운다
            applyFirstPage(page);
            return;
        }
        receive(page.items.filter((item) => !item.read));
        unread.value = page.unread;
        latestId.value = summary.latestId;
    }

    /** 새 알림 도착을 받기 시작한다(폴링) — 셸이 한 번 부르고, 돌려받은 함수로 끝낸다 */
    function startWatching(): () => void {
        return api.subscribe({
            knownLatestId: () => latestId.value,
            isPaused: () => open.value,
            onSummary: (summary) => {
                unread.value = summary.unread;
            },
            onNew: onPolled,
        });
    }

    return { items, nextCursor, open, loading, loadingMore, failed, unread, osPermission, reload, ensureLoaded, loadMore, markRead, markAllRead, follow, requestOsPermission, refreshOsPermission, receive, startWatching };
}
