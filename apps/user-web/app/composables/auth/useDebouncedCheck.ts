/**
 * 입력이 멈춘 뒤 서버에 확인하는 것(이메일·닉네임 중복 확인)의 공통 동작 — 원본(community) `composables/useDebouncedCheck.ts` 를 옮겼다. 지금은 이메일 가입(`/signup`)만 쓴다.
 *
 * - **디바운스**: 마지막 입력 뒤 `delay`(기본 400ms)가 지나야 호출한다.
 * - **늦게 온 응답이 덮어쓰지 않는다**: 요청마다 번호를 매기고, 새 입력이 오면 앞 요청은 끊는다(`AbortController`).
 *   끊지 못한 응답이 나중에 도착해도 번호가 달라 버린다.
 *
 * 상태는 이 호출 하나가 든다(화면 하나의 값이라 `useState` 가 아니다).
 */
export function useDebouncedCheck<T>(check: (value: string, signal: AbortSignal) => Promise<T>, delay = 400) {
    /** 마지막으로 성공한 확인 결과. 새로 확인을 시작하면 비운다 */
    const result = shallowRef<T | null>(null) as Ref<T | null>;
    /** 확인 중(디바운스 대기 + 요청 중) */
    const pending = ref(false);
    /** 확인 요청이 실패했다(네트워크 등). 사용자가 다시 입력하면 비운다 */
    const failed = ref(false);

    let timer: ReturnType<typeof setTimeout> | null = null;
    let controller: AbortController | null = null;
    let seq = 0;

    function cancel() {
        if (timer !== null) clearTimeout(timer);
        timer = null;
        seq++; // 날아가 있는 응답을 무효로 만든다
        controller?.abort();
        controller = null;
        pending.value = false;
    }

    async function run(value: string) {
        const mine = ++seq;
        controller?.abort();
        controller = new AbortController();
        try {
            const answer = await check(value, controller.signal);
            if (mine !== seq) return;
            result.value = answer;
            failed.value = false;
        } catch {
            if (mine !== seq) return;
            result.value = null;
            failed.value = true;
        } finally {
            if (mine === seq) pending.value = false;
        }
    }

    /** 입력이 바뀔 때마다 부른다. 빈 값이면 확인하지 않고 초기 상태로 돌아간다 */
    function schedule(value: string) {
        cancel();
        result.value = null;
        failed.value = false;
        if (!value) return;
        pending.value = true;
        timer = setTimeout(() => {
            timer = null;
            void run(value);
        }, delay);
    }

    /** 확인을 멈추고 결과를 비운다 */
    function reset() {
        cancel();
        result.value = null;
        failed.value = false;
    }

    onScopeDispose(cancel);

    return { result, pending, failed, schedule, reset };
}
