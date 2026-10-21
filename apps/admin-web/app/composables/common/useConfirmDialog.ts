/**
 * 확인 대화상자 — 되돌릴 수 없는 동작(삭제, 로그아웃)에 한 번 더 묻는다.
 * 그리는 것은 `AppConfirm`(레이아웃에 한 번 놓는다)이고, 여기는 "묻고 답을 기다리는" 것만 한다.
 *
 * ```ts
 * const { confirm } = useConfirmDialog();
 * if (!(await confirm({ title: '로그아웃할까요?', confirmLabel: '로그아웃' }))) return;
 * ```
 *
 * 바깥을 누르거나 Esc 를 누르면 취소(`false`)다. 이미 떠 있는 대화상자가 있을 때 또 부르면 앞의 것은 취소로 닫힌다.
 * ⚠ PrimeVue 의 `useConfirm` 과 이름이 겹치지 않게 `useConfirmDialog` 로 한다. user-web 의 것에서 선택지 여럿(`choose`)을 뺐다 — 필요한 화면이 생기면 그때 가져온다.
 */
export interface ConfirmOptions {
    title: string;
    message?: string;
    confirmLabel?: string;
    cancelLabel?: string;
    /** `danger` 는 확인 버튼이 붉다(삭제·버리기) */
    tone?: 'primary' | 'danger';
}

export interface ConfirmState extends ConfirmOptions {
    open: boolean;
}

/** 답을 기다리는 약속 — 직렬화하지 않는 값이라 `useState` 밖(모듈)에 둔다(SPA 라 서버와 공유될 일이 없다) */
let pending: ((confirmed: boolean) => void) | null = null;

export function useConfirmDialog() {
    const t = useTranslate();
    const state = useState<ConfirmState>('app-confirm', () => ({ open: false, title: '' }));

    /** 답을 정하고 닫는다 */
    function settle(confirmed: boolean): void {
        state.value = { ...state.value, open: false };
        const done = pending;
        pending = null;
        done?.(confirmed);
    }

    function confirm(options: ConfirmOptions): Promise<boolean> {
        pending?.(false);
        return new Promise<boolean>((resolve) => {
            pending = resolve;
            state.value = { tone: 'primary', confirmLabel: t('common.confirm'), cancelLabel: t('common.cancel'), ...options, open: true };
        });
    }

    /** (AppConfirm 전용) 확인 버튼 · 취소(바깥 누르기·Esc 포함) */
    const accept = () => settle(true);
    const cancel = () => settle(false);

    return { state, confirm, accept, cancel };
}
