/**
 * 토스트 — 화면 아래에 잠깐(기본 3초) 떴다가 사라지는 알림 (design 01-shell 8).
 * 성공·실패·안내 세 가지 톤. 그리는 것은 `AppToaster`(레이아웃에 한 번 놓는다)다.
 *
 * ⚠ PrimeVue 의 `useToast`(자동 import) 와 이름이 겹치지 않게 `useAppToast` 로 한다.
 * 상태를 `useState` 에 두므로 화면이 바뀌어도(글을 등록하고 상세로 이동해도) 토스트는 이어서 보인다.
 */
export type ToastTone = 'default' | 'success' | 'error';

/** 토스트 오른쪽의 행동 단추 — 차단·차단 해제의 "실행 취소"(4-3) */
export interface ToastAction {
    label: string;
    /** 누르면 부른다. 부르고 나면 토스트는 닫힌다 */
    run: () => void;
}

export interface ToastItem {
    id: number;
    tone: ToastTone;
    message: string;
    /** 행동 단추의 글자 — 없으면 단추도 없다. 누르면 부를 함수는 직렬화하지 않는 값이라 `useState` 밖(모듈)에 둔다 */
    actionLabel?: string;
}

export interface ToastOptions {
    tone?: ToastTone;
    /** 보이는 시간(ms). 기본 3초 */
    life?: number;
    /** 행동 단추 — 있으면 같은 문구의 토스트가 이미 떠 있어도 새로 띄운다(되돌릴 길이 사라지지 않게) */
    action?: ToastAction;
}

const DEFAULT_LIFE_MS = 3000;
let nextId = 1;
/** 행동 단추를 눌렀을 때 부를 함수 — 토스트 id 별. 토스트가 사라지면 함께 버린다 */
const actions = new Map<number, ToastAction['run']>();

export function useAppToast() {
    const toasts = useState<ToastItem[]>('app-toasts', () => []);

    function dismiss(id: number): void {
        actions.delete(id);
        toasts.value = toasts.value.filter((t) => t.id !== id);
    }

    /** 행동 단추를 눌렀다 — 함수를 부르고 토스트를 닫는다 */
    function act(id: number): void {
        const run = actions.get(id);
        dismiss(id);
        run?.();
    }

    /** 토스트를 띄운다. 같은 문구가 이미 떠 있으면 새로 쌓지 않는다(행동 단추가 있는 토스트는 앞의 것을 치우고 새로 띄운다) */
    function show(message: string, options: ToastOptions = {}): void {
        if (options.action) toasts.value.filter((t) => t.message === message).forEach((t) => dismiss(t.id));
        else if (toasts.value.some((t) => t.message === message)) return;
        const item: ToastItem = { id: nextId++, tone: options.tone ?? 'default', message, actionLabel: options.action?.label };
        if (options.action) actions.set(item.id, options.action.run);
        // 최대 3개까지만 쌓는다(밀려난 토스트의 행동 함수도 함께 버린다)
        const kept = toasts.value.slice(-2);
        toasts.value.filter((t) => !kept.includes(t)).forEach((t) => actions.delete(t.id));
        toasts.value = [...kept, item];
        setTimeout(() => dismiss(item.id), options.life ?? DEFAULT_LIFE_MS);
    }

    return {
        toasts,
        show,
        success: (message: string, life?: number) => show(message, { tone: 'success', life }),
        error: (message: string, life?: number) => show(message, { tone: 'error', life }),
        dismiss,
        act,
    };
}
