/**
 * 쪽지 화면의 주기 조회 — **탭이 보이는 동안만** 일정 간격으로 `task` 를 부른다(쪽지함 60초 · 열려 있는 대화방 10초).
 * 탭이 가려지면 멈추고, 다시 보이면 곧바로 한 번 부르고 이어서 돈다. 화면이 사라지면 스스로 멈춘다. 마운트 직후에는 부르지 않는다(처음 받는 일은 화면이 따로 한다).
 *
 * ⚠ setup 에서 부른다(`onMounted`·`onBeforeUnmount` 를 쓴다).
 * @param task 부를 일 — 실패는 이 안에서 삼킨다
 * @param intervalMs 간격(밀리초)
 */
export function useDmPoll(task: () => void | Promise<void>, intervalMs: number): void {
    let timer: ReturnType<typeof setInterval> | null = null;
    const visible = () => typeof document !== 'undefined' && document.visibilityState === 'visible';

    const stop = () => {
        if (timer !== null) clearInterval(timer);
        timer = null;
    };
    const start = () => {
        stop();
        if (!visible()) return;
        timer = setInterval(() => {
            if (visible()) void task();
        }, intervalMs);
    };
    const onVisibility = () => {
        if (visible()) {
            void task();
            start();
        } else {
            stop();
        }
    };

    onMounted(() => {
        start();
        document.addEventListener('visibilitychange', onVisibility);
    });
    onBeforeUnmount(() => {
        stop();
        document.removeEventListener('visibilitychange', onVisibility);
    });
}
