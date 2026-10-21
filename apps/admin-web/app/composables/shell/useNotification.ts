import type { NotificationItem, NotificationKind } from '~/composables/shell/api/useNotificationApi.types';
import type { NotificationToastDetail, OsNotificationPermission } from '~/composables/shell/useNotification.types';

/**
 * 관리자 알림 — 상단 종(안 읽은 수)과 알림 서랍이 **같은 상태**를 본다(`useState`).
 *
 * `items`: `undefined` = 아직 안 물어봄 · 배열 = 받았음(비었으면 알림 없음). 이미 받았으면 다시 묻지 않는다(`ensureLoaded`) — 서랍을 열 때 `reload` 로 새로 받는다.
 * 서버와 말하는 것은 `useNotificationApi` 다. 여기는 상태와 낙관적 갱신, 새 알림 도착 알림만 든다.
 *
 * ── 새 알림이 도착하면 (`receive`) ─ 화면 설정(`notifyToast` · `notifyOs`)을 따른다
 *   창을 보고 있다                              → 토스트(종 아래 오른쪽 위). 서랍이 열려 있으면 목록에 이미 떠오르므로 토스트를 또 얹지 않는다
 *   창이 가려졌다 · 다른 창에 초점이 있다         → OS 알림(권한이 허용됐을 때). 못 쓰면 토스트로 물러선다
 * `receive` 를 부르는 쪽은 `useNotificationApi().subscribe` 뿐이다(`startWatching`) — 도착 경로는 아직 목업이다.
 */

/** 새 알림 토스트 묶음 이름 — 셸(`layouts/default.vue`)이 이 묶음에 「알림 이력 보기」 단추가 있는 틀을 건다 */
export const NOTIFICATION_TOAST_GROUP = 'admin-notification';

/** 토스트가 스스로 꺼지는 시간(ms). 놓쳐도 종의 숫자와 서랍에 남는다 */
const TOAST_LIFE_MS = 6000;
/** 토스트·OS 알림에 한꺼번에 적는 제목 줄 수 */
const ANNOUNCE_MAX = 3;

const TOAST_SEVERITY: Record<NotificationKind, 'info' | 'success' | 'warn'> = {
    info: 'info',
    success: 'success',
    warning: 'warn',
};

function currentPermission(): OsNotificationPermission {
    if (!import.meta.client || !('Notification' in window)) return 'unsupported';
    return Notification.permission;
}

export function useNotification() {
    const t = useTranslate();
    const api = useNotificationApi();
    const appToast = useAppToast();
    // ⚠ 도착 알림은 비동기 콜백에서 오므로 토스트 서비스·화면 설정·사이트 이름은 setup 때 붙잡아 둔다 (composables.md Nuxt 문맥 함정)
    const toast = useToast();
    const { prefs } = useLayoutPrefs();
    const { siteLabel } = useAdminAuth();

    const items = useState<NotificationItem[] | undefined>('admin-notifications', () => undefined);
    /** 서랍이 열려 있는가 — 종 · 토스트의 「알림 이력 보기」 · OS 알림 누름이 같은 서랍을 연다 */
    const open = useState<boolean>('admin-notification-open', () => false);
    const loading = useState<boolean>('admin-notification-loading', () => false);
    const failed = useState<boolean>('admin-notification-failed', () => false);
    const osPermission = useState<OsNotificationPermission>('admin-notification-os', currentPermission);

    /** 안 읽은 수 — 종 뱃지와 서랍 머리글이 같은 값을 본다 */
    const unread = computed(() => items.value?.filter((item) => !item.read).length ?? 0);

    async function reload(): Promise<void> {
        if (loading.value) return;
        loading.value = true;
        failed.value = false;
        try {
            items.value = (await api.list()).items;
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

    /** 알림 하나를 읽음으로 — 화면을 먼저 바꾸고 서버가 실패하면 되돌린다 */
    async function markRead(id: string): Promise<void> {
        const before = items.value;
        if (!before?.some((item) => item.id === id && !item.read)) return;
        items.value = before.map((item) => (item.id === id ? { ...item, read: true } : item));
        try {
            await api.read(id);
        } catch {
            items.value = before;
            appToast.error(t('notification.readFailed'));
        }
    }

    /** 모두 읽음으로 — 화면을 먼저 바꾸고 서버가 실패하면 되돌린다 */
    async function markAllRead(): Promise<void> {
        const before = items.value;
        if (!before || unread.value === 0) return;
        items.value = before.map((item) => ({ ...item, read: true }));
        try {
            await api.readAll();
        } catch {
            items.value = before;
            appToast.error(t('notification.readFailed'));
        }
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
        // 가장 눈에 띄어야 하는 종류(경고 → 성공 → 안내)로 색을 정한다
        const worst = arrived.some((item) => item.kind === 'warning') ? 'warning' : arrived.some((item) => item.kind === 'success') ? 'success' : 'info';
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
     * 새 알림이 도착했다 — 목록에 더하고 설정대로 알린다. **도착 경로가 부르는 유일한 진입점**이다.
     * 목록을 아직 안 받았으면(`undefined`) 더하지 않는다 — 받을 때 서버가 이 알림까지 준다.
     */
    function receive(arrived: NotificationItem[]): void {
        const known = new Set(items.value?.map((item) => item.id));
        const fresh = arrived.filter((item) => !known.has(item.id));
        if (!fresh.length) return;
        if (items.value) items.value = [...fresh, ...items.value];

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

    /** 새 알림 도착을 받기 시작한다 — 셸이 한 번 부르고, 돌려받은 함수로 끝낸다 */
    function startWatching(): () => void {
        return api.subscribe(receive);
    }

    return { items, open, loading, failed, unread, osPermission, reload, ensureLoaded, markRead, markAllRead, requestOsPermission, refreshOsPermission, receive, startWatching };
}
