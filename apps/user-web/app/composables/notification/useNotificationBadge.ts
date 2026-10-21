import UtilNotification from '~/utils/util-notification';

/**
 * 안 읽은 알림 수 — 헤더의 종·하단 탭의 알림이 함께 보는 **공유 상태**(`useState` 하나)이고,
 * 셸이 뜰 때 한 번 주기 조회를 시작한다(`start`, `layouts/default.vue`).
 *
 * 주기 조회: 로그인해 있고 **탭이 보일 때** 약 60초 간격 + 탭이 다시 보일 때 즉시 + 로그인 직후 즉시.
 * 로그아웃하면 멈추고 0 으로 돌린다. 요청이 실패하면 조용히 넘어간다(점 상태 유지 — 다음 때 다시 묻는다).
 * (세션이 끊겨 401 이 오면 요청 통로(`useApiClient`)가 로그인 화면으로 보낸다. 그것은 여기서 다루지 않는다.)
 *
 * **낙관적 갱신**: 알림을 읽거나 "모두 읽음"을 누르면 서버 응답을 기다리지 않고 숫자를 줄이고(`markedRead`·`markedAllRead`),
 * 그 뒤 `refresh` 로 서버 값에 맞춘다. 숫자를 손으로 바꾸면 순번(`seq`)을 올려서, **그 전에 나간 조회의 늦은 응답이 줄인 숫자를 덮지 못하게** 한다.
 * 서버가 세는 값은 100 까지다(`UtilNotification.badgeText` 가 99 를 넘으면 "99+" 로 보인다).
 */

/** 모듈 안에 둔다 — 컴포넌트 여러 곳이 `useNotificationBadge()` 를 불러도 같은 순번·같은 시작 표시를 본다 */
let seq = 0;
let started = false;

export function useNotificationBadge() {
    const api = useNotificationApi();
    const { user } = useAuth();
    const t = useTranslate();

    /** 안 읽은 알림 수(서버 상한 100). 비회원·로그아웃은 0 */
    const count = useState<number>('notification-unread', () => 0);
    /** 종·탭에 보일 글자 — 0 이면 빈 문자열, 99 를 넘으면 "99+" */
    const text = computed(() => UtilNotification.badgeText(count.value));
    /** 종·탭 링크의 이름(스크린리더용) — "알림, 안 읽은 알림 3개" */
    const label = computed(() => UtilNotification.badgeLabel(t, count.value));

    /** 서버에 안 읽은 수를 묻는다. 실패는 조용히 넘기고, 묻는 사이 숫자를 손으로 바꿨으면 늦은 응답은 버린다 */
    async function refresh(): Promise<void> {
        if (!user.value) return;
        const mine = ++seq;
        try {
            const res = await api.unreadCount();
            if (mine !== seq) return;
            count.value = UtilNotification.clampCount(res.count);
        } catch {
            /* 점 상태를 그대로 둔다 */
        }
    }

    /** 서버가 이미 알려 준 값으로 맞춘다(알림 목록 응답의 `unreadCount`) */
    function sync(value: number): void {
        seq++;
        count.value = UtilNotification.clampCount(value);
    }

    /** 알림 하나를 읽었다 — 안 읽은 알림이었다면 하나 줄인다(낙관적) */
    function markedRead(wasUnread: boolean): void {
        seq++;
        count.value = UtilNotification.afterRead(count.value, wasUnread);
    }

    /** "모두 읽음" — 화면에 불러온 안 읽은 만큼 줄인다(낙관적). 나머지는 이어서 `refresh` 가 서버 값으로 맞춘다 */
    function markedAllRead(unreadShown: number): void {
        seq++;
        count.value = UtilNotification.afterReadAll(count.value, unreadShown);
    }

    /** 0 으로 돌린다(로그아웃) — 이미 나간 조회의 응답도 버린다 */
    function reset(): void {
        seq++;
        count.value = 0;
    }

    /**
     * 주기 조회를 시작한다 — **셸(레이아웃)이 뜰 때 한 번만** 부른다. 셸이 사라지면 스스로 멈춘다.
     * 이미 시작돼 있으면 아무 일도 하지 않는다(여러 곳이 불러도 타이머는 하나).
     */
    function start(): void {
        if (started || typeof document === 'undefined') return;
        started = true;

        let timer: ReturnType<typeof setInterval> | null = null;
        const visible = () => document.visibilityState === 'visible';
        const stop = () => {
            if (timer !== null) clearInterval(timer);
            timer = null;
        };
        /** 즉시 한 번 묻고, 보이는 동안 60초마다 다시 묻는다. 로그인 전이거나 탭이 가려져 있으면 쉰다 */
        const run = () => {
            stop();
            if (!user.value || !visible()) return;
            void refresh();
            timer = setInterval(() => {
                if (visible()) void refresh();
            }, UtilNotification.POLL_INTERVAL_MS);
        };
        const onVisibility = () => (visible() ? run() : stop());

        document.addEventListener('visibilitychange', onVisibility);
        // 로그인하면 바로 한 번(`immediate` 는 이미 로그인한 채로 셸이 뜬 경우), 로그아웃하면 멈추고 0 으로
        const unwatch = watch(
            () => user.value?.id ?? null,
            (id) => {
                if (id) run();
                else {
                    stop();
                    reset();
                }
            },
            { immediate: true },
        );

        onScopeDispose(() => {
            stop();
            unwatch();
            document.removeEventListener('visibilitychange', onVisibility);
            started = false;
        });
    }

    return { count, text, label, refresh, sync, markedRead, markedAllRead, reset, start };
}
