/**
 * 카운트다운 — 로그인 잠금 남은 시간에 쓴다.
 *
 * **끝나는 시각(밀리초)을 든다.** 초를 하나씩 빼는 방식은 탭이 백그라운드에서 느려지면 실제보다 오래 남는다.
 * `endsAt` 를 넘기면 여러 화면이 같은 카운트다운을 본다.
 *
 * ```ts
 * const lock = useCountdown(undefined, () => focusPassword());
 * lock.start(600);            // 10분
 * lock.remaining.value        // 남은 초
 * ```
 */
export function useCountdown(endsAt: Ref<number> = ref(0), onFinish?: () => void) {
    const now = ref(Date.now());
    let timer: ReturnType<typeof setInterval> | null = null;

    const remaining = computed(() => Math.max(0, Math.ceil((endsAt.value - now.value) / 1000)));
    const active = computed(() => remaining.value > 0);

    function stopTimer() {
        if (timer !== null) clearInterval(timer);
        timer = null;
    }

    function tick() {
        now.value = Date.now();
        if (endsAt.value <= now.value) stopTimer();
    }

    // 끝나는 시각이 미래로 바뀌면 타이머를 켠다
    watch(
        endsAt,
        (value) => {
            now.value = Date.now();
            if (value > now.value && timer === null) timer = setInterval(tick, 250);
        },
        { immediate: true },
    );

    // 저절로 끝났을 때만 알린다(stop() 은 끝나는 시각을 0 으로 되돌리므로 여기서 걸러진다)
    watch(active, (isActive, wasActive) => {
        if (wasActive && !isActive && endsAt.value !== 0) onFinish?.();
    });

    function start(seconds: number) {
        endsAt.value = Date.now() + Math.max(0, seconds) * 1000;
    }

    function stop() {
        endsAt.value = 0;
        now.value = Date.now();
        stopTimer();
    }

    onScopeDispose(stopTimer);

    return { remaining, active, start, stop };
}
