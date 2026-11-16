/**
 * 날짜 칸 키보드 입력 지킴이 — PrimeVue DatePicker 는 덜 친 글자(「2020-06-1」)도 날짜(6월 1일)로 읽어 값으로 삼고,
 * 그 값을 형식대로 다시 쓴 글자(「2020-06-01」)를 곧바로 입력칸에 넣는다(`defaultValue: inputFieldValue`). 그래서 「2020-06-15」를 치면
 * 「2020-06-051」 이 되고 칸을 벗어나면 6월 1일로 굳는다(공개 QA P-2 — 사업자 신청 개업일 등).
 *
 * 고르개의 읽기 함수(`parseValue`)만 감싼다:
 * - 형식 길이(`minLength` — `UtilDatePicker.typingLength`)보다 짧은 글자는 「아직 날짜가 아님」으로 읽기를 거절한다(고르개는 값을 바꾸지 않는다).
 * - 짧은 채로 칸을 떠나려 하면(「2020-6-15」처럼 0 을 빼고 친 경우) 그 직전에 한 번 읽게 한다 — 읽을 수 없으면 고르개가 원래 값으로 되돌린다.
 *   안쪽 입력칸(InputText)은 받은 값만 그리는 칸이라, 달력이 닫히는 등 다시 그려지면 친 글자가 지워진다. 그래서 칸을 벗어나는 순간(blur)이 아니라
 *   그보다 앞선 Tab·Enter·Esc 키와 다른 곳 누르기(pointerdown)에서 읽힌다.
 *
 * 쓰는 법: `<IconField @focus.capture="typing.onFocusCapture" @keydown.capture="typing.onKeydownCapture" @blur.capture="typing.onBlurCapture">`
 * `<DatePicker :ref="typing.bind" …/>`. `parseValue` 는 고르개가 입력·Enter 에서 부르는 내부 함수다(PrimeVue 5 — 값을 글자로 넘길 때만 다른 곳에서도
 * 부르는데, 우리 감싸개는 Date 를 넘긴다).
 * @param {() => number} minLength - 다 친 값의 글자 수.
 * @returns 고르개 ref 함수와 감싼 요소에 붙일 처리기.
 */
export function useDateTypingGuard(minLength: () => number) {
    /** 칸을 떠날 때 — 짧은 글자도 읽게 한다 */
    let allowShort = false;
    const patched = new WeakSet<object>();
    /** 지금 입력 중인 칸 — 다른 곳을 누를 때 읽게 한다 */
    let active: HTMLInputElement | null = null;

    function isShort(text: unknown): boolean {
        if (typeof text !== 'string') return false;
        const value = text.trim();
        return value !== '' && value.length < minLength();
    }

    /** 짧은 글자를 고르개에 한 번 읽힌다(고르개의 input 처리기를 그대로 부른다) */
    function commitShort(input: HTMLInputElement | null): void {
        if (!input || !isShort(input.value)) return;
        allowShort = true;
        try {
            input.dispatchEvent(new Event('input', { bubbles: true }));
        } finally {
            allowShort = false;
        }
    }

    /** 고르개 ref — 새로 그려진 고르개(형식이 바뀌면 `:key` 로 새로 만든다)마다 한 번 감싼다 */
    function bind(instance: unknown): void {
        if (!instance || typeof instance !== 'object' || patched.has(instance)) return;
        const picker = instance as { parseValue?: (text: string) => unknown };
        const original = picker.parseValue;
        if (typeof original !== 'function') return;
        picker.parseValue = (text: string) => {
            if (!allowShort && isShort(text)) throw new Error('date typing not finished');
            return original(text);
        };
        patched.add(instance);
    }

    function onPointerDown(): void {
        commitShort(active);
    }

    function onFocusCapture(event: FocusEvent): void {
        if (!(event.target instanceof HTMLInputElement)) return;
        active = event.target;
        document.addEventListener('pointerdown', onPointerDown, true);
    }

    function onKeydownCapture(event: KeyboardEvent): void {
        if (event.key === 'Tab' || event.key === 'Enter' || event.key === 'Escape') {
            commitShort(event.target instanceof HTMLInputElement ? event.target : null);
        }
    }

    function onBlurCapture(event: FocusEvent): void {
        if (event.target instanceof HTMLInputElement) commitShort(event.target);
        if (event.target === active) {
            active = null;
            document.removeEventListener('pointerdown', onPointerDown, true);
        }
    }

    onBeforeUnmount(() => document.removeEventListener('pointerdown', onPointerDown, true));

    return { bind, onFocusCapture, onKeydownCapture, onBlurCapture };
}
