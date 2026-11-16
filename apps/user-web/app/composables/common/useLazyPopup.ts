import { nextTick, ref, shallowRef, watch } from 'vue';

/** PrimeVue 팝업(`Menu`·`Popover`)의 열고 닫기 — 둘째 인자로 기준 요소를 따로 받는다 */
export interface LazyPopupHandle {
    toggle: (event: Event, target?: Element | null) => void;
    hide?: () => void;
}

/**
 * 처음 열 때 코드를 받는 팝업(`Lazy` 컴포넌트 + `v-if="mounted"` + `ref` 를 `handle` 에) — 첫 화면 조각에서 팝업 부품(PrimeVue Menu 등)을 뺀다
 * (관리자 QA 2026-11-17 E2, docs/qa/home-bundle-size-2026-11-17.md).
 *
 * 첫 `toggle` 은 `mounted` 를 켜고 부품이 뜰 때까지 기다린 뒤 연다. 기다리는 사이 이벤트의 `currentTarget` 이 비므로 누른 요소를 기준(`target`)으로 따로 넘긴다.
 * 두 번째부터는 바로 연다.
 *
 * @example
 * const { mounted: menuMounted, handle: menu, toggle: toggleMenu } = useLazyPopup();
 * // <button @click="toggleMenu"> … <LazyMenu v-if="menuMounted" ref="menu" popup … />
 */
export function useLazyPopup<T extends LazyPopupHandle = LazyPopupHandle>() {
    const mounted = ref(false);
    const handle = shallowRef<T | null>(null);

    /** 부품이 떠서 템플릿 ref 가 잡힐 때까지 */
    function ready(): Promise<T> {
        if (handle.value) return Promise.resolve(handle.value);
        return new Promise((resolve) => {
            const stop = watch(handle, (value) => {
                if (!value) return;
                stop();
                resolve(value);
            });
        });
    }

    async function toggle(event: Event): Promise<void> {
        const target = (event.currentTarget as Element | null) ?? null;
        if (!handle.value) {
            mounted.value = true;
            await ready();
            await nextTick();
        }
        handle.value?.toggle(event, target);
    }

    function hide(): void {
        handle.value?.hide?.();
    }

    return { mounted, handle, toggle, hide };
}
