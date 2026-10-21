/**
 * 토스트 — 화면 아래에 잠깐(기본 3초) 떴다가 사라지는 알림. 성공·실패·안내 세 가지 톤.
 * 그리는 것은 `AppToaster`(레이아웃에 한 번 놓는다)다.
 *
 * ⚠ PrimeVue 의 `useToast`(자동 import) 와 이름이 겹치지 않게 `useAppToast` 로 한다.
 * 상태를 `useState` 에 두므로 화면이 바뀌어도(로그아웃하고 로그인 화면으로 이동해도) 토스트는 이어서 보인다.
 * user-web 의 것에서 "행동 단추(실행 취소)"를 뺐다 — 필요한 화면이 생기면 그때 가져온다.
 */
export type ToastTone = 'default' | 'success' | 'error';

export interface ToastItem {
    id: number;
    tone: ToastTone;
    message: string;
}

export interface ToastOptions {
    tone?: ToastTone;
    /** 보이는 시간(ms). 기본 3초 */
    life?: number;
}

const DEFAULT_LIFE_MS = 3000;
let nextId = 1;

export function useAppToast() {
    const toasts = useState<ToastItem[]>('app-toasts', () => []);

    function dismiss(id: number): void {
        toasts.value = toasts.value.filter((t) => t.id !== id);
    }

    /** 토스트를 띄운다. 같은 문구가 이미 떠 있으면 새로 쌓지 않는다. 최대 3개까지만 쌓는다 */
    function show(message: string, options: ToastOptions = {}): void {
        if (toasts.value.some((t) => t.message === message)) return;
        const item: ToastItem = { id: nextId++, tone: options.tone ?? 'default', message };
        toasts.value = [...toasts.value.slice(-2), item];
        setTimeout(() => dismiss(item.id), options.life ?? DEFAULT_LIFE_MS);
    }

    return {
        toasts,
        show,
        success: (message: string, life?: number) => show(message, { tone: 'success', life }),
        error: (message: string, life?: number) => show(message, { tone: 'error', life }),
        dismiss,
    };
}
