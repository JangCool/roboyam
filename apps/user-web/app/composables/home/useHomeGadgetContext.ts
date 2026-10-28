import type { InjectionKey, Ref } from 'vue';
import type { HomeGadgetLook, HomeGadgetLookInput, HomeGadgetLookOptions, HomeGadgetShared } from '~/composables/home/useHomeGadgetContext.types';
import UtilHomeGadget from '~/utils/util-home-gadget';

/**
 * 홈 가젯들이 나눠 쓰는 문맥 — 홈 페이지가 만든 방문 상태(폭·지금 시각)와 템플릿이 정한 가젯 모양(`classic`·`card`).
 * 서버와 말하지 않는다(데이터는 페이지가 `useHomeGadgetApi` 로 한 번 받아 템플릿에 내린다).
 * ⚠ setup 에서 부른다(`await` 뒤에는 provide/inject 문맥이 없다).
 */
const SHARED_KEY: InjectionKey<HomeGadgetShared> = Symbol('home-gadget-shared');
const LOOK_KEY: InjectionKey<HomeGadgetLook> = Symbol('home-gadget-look');
const LOOK_OPTIONS_KEY: InjectionKey<HomeGadgetLookOptions> = Symbol('home-gadget-look-options');
const STACKED_KEY: InjectionKey<Readonly<Ref<boolean>>> = Symbol('home-gadget-stacked');

/** 세부 모양 기본값 — 값을 주지 않는 템플릿의 모양(한 열도 지금 모양 그대로) */
const DEFAULT_LOOK_OPTIONS: Readonly<HomeGadgetLookOptions> = {
    heroSplit: false,
    galleryColumns: 3,
    mobile: { order: 'reading', highlightScroll: false, heroBleed: false, galleryScroll: false, groupBuyScroll: false, groupsScroll: false, boardCardsScroll: false },
};
/** 홈 격자 밖(한 열을 모른다) — 늘 false */
const NOT_STACKED: Readonly<Ref<boolean>> = shallowRef(false);

function createShared(): HomeGadgetShared {
    const wide = useMediaQuery(UtilHomeGadget.WIDE_QUERY);
    const now = ref(Date.now());
    if (typeof window !== 'undefined') {
        const timer = window.setInterval(() => {
            now.value = Date.now();
        }, 60_000);
        onScopeDispose(() => window.clearInterval(timer));
    }
    return { wide, now };
}

/**
 * 홈 페이지가 부른다 — 이 방문의 폭·지금 시각을 만들어 아래 템플릿·가젯이 나눠 쓰게 한다.
 * @returns {HomeGadgetShared} 공유 상태.
 */
export function provideHomeGadgetShared(): HomeGadgetShared {
    const shared = createShared();
    provide(SHARED_KEY, shared);
    return shared;
}

/**
 * 템플릿·가젯이 부른다 — 홈이 만든 공유 상태(홈 밖이면 자기 것을 만든다).
 * @returns {HomeGadgetShared} 공유 상태.
 */
export function useHomeGadgetShared(): HomeGadgetShared {
    return inject(SHARED_KEY, null) ?? createShared();
}

/**
 * 템플릿이 부른다 — 아래 가젯이 그릴 모양을 정한다.
 * @param {HomeGadgetLook} look - `classic`(기본형) · `card`(카드형).
 * @param {HomeGadgetLookInput} [options] - 세부 모양(선택) — 주지 않은 값은 기본값(한 열 모양 `mobile` 도 값마다).
 */
export function provideHomeGadgetLook(look: HomeGadgetLook, options?: HomeGadgetLookInput): void {
    provide(LOOK_KEY, look);
    provide(LOOK_OPTIONS_KEY, { ...DEFAULT_LOOK_OPTIONS, ...options, mobile: { ...DEFAULT_LOOK_OPTIONS.mobile, ...options?.mobile } });
}

/**
 * 홈 격자(`HomeGadgetGrid`)가 부른다 — 지금 한 열(`stack`)로 그리는지 아래 가젯에 알린다. 가젯은 이것과 템플릿의 한 열 모양(`mobile`)을 함께 보고 한 열 모양을 고른다(둘 다일 때만 — 데스크톱·태블릿 격자는 그대로).
 * @param {Readonly<Ref<boolean>>} stacked - 한 열인가.
 */
export function provideHomeGadgetStacked(stacked: Readonly<Ref<boolean>>): void {
    provide(STACKED_KEY, stacked);
}

/**
 * 가젯이 부른다 — 홈 격자가 지금 한 열로 그리는가(격자 밖이면 늘 false).
 * @returns {Readonly<Ref<boolean>>} 한 열인가.
 */
export function useHomeGadgetStacked(): Readonly<Ref<boolean>> {
    return inject(STACKED_KEY, NOT_STACKED);
}

/**
 * 가젯이 부른다 — 템플릿이 정한 모양(템플릿 밖이면 기본형).
 * @returns {HomeGadgetLook} 모양.
 */
export function useHomeGadgetLook(): HomeGadgetLook {
    return inject(LOOK_KEY, 'classic');
}

/**
 * 가젯이 부른다 — 템플릿이 고른 세부 모양(템플릿 밖이거나 주지 않았으면 기본값).
 * @returns {HomeGadgetLookOptions} 세부 모양.
 */
export function useHomeGadgetLookOptions(): Readonly<HomeGadgetLookOptions> {
    return inject(LOOK_OPTIONS_KEY, DEFAULT_LOOK_OPTIONS);
}
