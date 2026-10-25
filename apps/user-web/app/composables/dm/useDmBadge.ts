import UtilNotification from '~/utils/util-notification';

/**
 * 안 읽은 쪽지가 있는 **대화 수** — 내 정보 메뉴의 "쪽지함" 줄·데스크톱 헤더의 쪽지 아이콘이 함께 보는 **공유 상태**(`useState` 하나)이고,
 * 셸이 뜰 때 한 번 주기 조회를 시작한다(`start`, `layouts/default.vue`). 서버는 `GET /api/dm/unread-count` 의 `count`(상한 100)로 알려 준다.
 *
 * `useNotificationBadge` 와 같은 방식이다: 로그인해 있고 **탭이 보일 때** 약 60초 간격 + 탭이 다시 보일 때 즉시 + 로그인 직후 즉시, 로그아웃하면 멈추고 0 으로.
 * 대화방을 읽음 처리했거나(`markedRead`) 서버가 이미 알려 준 값이 있으면(`sync`) 그 값으로 바로 맞추고, 손으로 바꾸면 순번(`seq`)을 올려
 * **그 전에 나간 조회의 늦은 응답이 덮지 못하게** 한다. 요청이 실패하면 조용히 넘어간다.
 */

/** 모듈 안에 둔다 — 컴포넌트 여러 곳이 `useDmBadge()` 를 불러도 같은 순번·같은 시작 표시를 본다 */
let seq = 0;
let started = false;

export function useDmBadge() {
    const api = useDmApi();
    const { user } = useAuth();
    const t = useTranslate();

    /** 안 읽은 대화 수(서버 상한 100). 비회원·로그아웃은 0 */
    const count = useState<number>('dm-unread-threads', () => 0);
    /** 아이콘·줄에 보일 글자 — 0 이면 빈 문자열, 99 를 넘으면 "99+" */
    const text = computed(() => UtilNotification.badgeText(count.value));
    /** 링크의 이름(스크린리더용) — "쪽지함" · "쪽지함, 안 읽은 대화 3개" */
    const label = computed(() => UtilNotification.dmBadgeLabel(t, count.value));

    /** 서버에 안 읽은 대화 수를 묻는다. 실패는 조용히 넘기고, 묻는 사이 숫자를 손으로 바꿨으면 늦은 응답은 버린다 */
    async function refresh(): Promise<void> {
        if (!user.value) return;
        const mine = ++seq;
        try {
            const res = await api.unreadCount();
            if (mine !== seq) return;
            count.value = UtilNotification.clampCount(res.count);
        } catch {
            /* 숫자를 그대로 둔다 */
        }
    }

    /** 서버가 이미 알려 준 값으로 맞춘다(`GET /api/me/dm` 응답의 `unreadThreads` · `GET /api/dm/unread-count` 의 `count`) */
    function sync(value: number): void {
        seq++;
        count.value = UtilNotification.clampCount(value);
    }

    /** 대화 하나를 읽음 처리했다 — 안 읽은 대화였다면 하나 줄인다(낙관적). 이어서 `refresh` 로 서버 값에 맞춘다 */
    function markedRead(wasUnread: boolean): void {
        seq++;
        count.value = UtilNotification.afterRead(count.value, wasUnread);
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

    return { count, text, label, refresh, sync, markedRead, reset, start };
}
