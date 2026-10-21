/**
 * 확인 대화상자 — 되돌릴 수 없는 동작(글·댓글 삭제, 로그아웃, 작성 중 이탈)에 한 번 더 묻는다 (design 01-shell 8).
 * 그리는 것은 `AppConfirm`(레이아웃에 한 번 놓는다)이고, 여기는 "묻고 답을 기다리는" 것만 한다.
 *
 * ```ts
 * const { confirm, choose } = useConfirmDialog();
 * if (!(await confirm({ title: '이 글을 삭제할까요?', message: '삭제한 글은 되돌릴 수 없어요.', confirmLabel: '삭제', tone: 'danger' }))) return;
 *
 * // 선택지가 셋 이상일 때(작성 중 이탈: 임시저장하고 나가기 · 버리고 나가기 · 계속 쓰기) — 위에서 아래로 쌓인다
 * const answer = await choose({ title: '…', choices: [{ key: 'save', label: '임시저장하고 나가기' }, { key: 'stay', label: '계속 쓰기', tone: 'neutral' }] });
 * ```
 *
 * 바깥을 누르거나 Esc 를 누르면 취소다(`confirm` 은 false, `choose` 는 null). 이미 떠 있는 대화상자가 있을 때 또 부르면 앞의 것은 취소로 닫힌다.
 * ⚠ PrimeVue 의 `useConfirm` 과 이름이 겹치지 않게 `useConfirmDialog` 로 한다.
 */
export interface ConfirmOptions {
    title: string;
    message?: string;
    confirmLabel?: string;
    cancelLabel?: string;
    /** `danger` 는 확인 버튼이 붉다(삭제·버리기) */
    tone?: 'primary' | 'danger';
}

/** `choose` 의 선택지 하나 — `primary` 는 강조(파랑), `danger` 는 붉은색, `neutral` 은 테두리만 */
export interface ChoiceOption<K extends string = string> {
    key: K;
    label: string;
    tone?: 'primary' | 'danger' | 'neutral';
}

export interface ChoiceOptions<K extends string = string> {
    title: string;
    message?: string;
    /** 위에서 아래로 쌓인다. 마지막(보통 "계속 쓰기")에 처음 초점이 간다 */
    choices: ChoiceOption<K>[];
}

export interface ConfirmState extends ConfirmOptions {
    open: boolean;
    /** 있으면 두 버튼 대신 이 선택지들을 쌓아서 보인다 */
    choices?: ChoiceOption[];
}

/** `confirm` 의 확인을 뜻하는 답 — `choose` 의 선택지 key 와 겹치지 않는 값이고, 화면은 `accept()` 로만 낸다 */
const CONFIRMED = '__confirmed__';

/** 답을 기다리는 약속 — 직렬화하지 않는 값이라 `useState` 밖(모듈)에 둔다(SPA 라 서버와 공유될 일이 없다). null 은 취소 */
let pending: ((answer: string | null) => void) | null = null;

export function useConfirmDialog() {
    const t = useTranslate();
    const state = useState<ConfirmState>('app-confirm', () => ({ open: false, title: '' }));

    /** 답을 정하고 닫는다. `null` 은 취소 */
    function settle(answer: string | null): void {
        state.value = { ...state.value, open: false };
        const done = pending;
        pending = null;
        done?.(answer);
    }

    function confirm(options: ConfirmOptions): Promise<boolean> {
        pending?.(null);
        return new Promise<boolean>((resolve) => {
            pending = (answer) => resolve(answer === CONFIRMED);
            state.value = { tone: 'primary', confirmLabel: t('common.confirm'), cancelLabel: t('common.cancel'), ...options, open: true };
        });
    }

    /** 선택지 여럿 중 하나를 고르게 한다. 고른 것의 key, 취소하면 null */
    function choose<K extends string>(options: ChoiceOptions<K>): Promise<K | null> {
        pending?.(null);
        return new Promise<K | null>((resolve) => {
            pending = (answer) => resolve(answer as K | null);
            state.value = { title: options.title, message: options.message, choices: options.choices, open: true };
        });
    }

    /** (AppConfirm 전용) 확인 버튼 · 취소(바깥 누르기·Esc 포함) · 선택지 고르기 */
    const accept = () => settle(CONFIRMED);
    const cancel = () => settle(null);
    const pick = (key: string) => settle(key);

    return { state, confirm, choose, accept, cancel, pick };
}
