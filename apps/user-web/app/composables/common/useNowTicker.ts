/**
 * 화면 여러 곳이 같이 보는 「지금」(밀리초, 1초마다) — 공동구매 마감 카운트다운처럼 카드가 많아도 타이머는 하나다.
 *
 * 보는 컴포넌트가 하나도 없으면 멈춘다. 끝나는 시각에서 빼서 쓰므로 탭이 백그라운드에서 느려져도 돌아오면 바로 맞는다.
 * 화면은 SPA(`ssr: false`)라 모듈에 하나만 둔다. setup 에서 부른다(타이머를 화면 수명에 묶는다).
 *
 * ```ts
 * const now = useNowTicker();
 * const left = computed(() => endsAtMs - now.value);
 * ```
 */
const now = ref(Date.now());
let users = 0;
let timer: ReturnType<typeof setInterval> | null = null;

export function useNowTicker(): Readonly<Ref<number>> {
    onMounted(() => {
        users++;
        now.value = Date.now();
        if (timer === null) timer = setInterval(() => (now.value = Date.now()), 1000);
    });
    onBeforeUnmount(() => {
        users = Math.max(0, users - 1);
        if (users === 0 && timer !== null) {
            clearInterval(timer);
            timer = null;
        }
    });
    return readonly(now);
}
