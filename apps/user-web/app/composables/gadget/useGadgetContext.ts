import type { InjectionKey, Ref, VNode } from 'vue';
import type { GadgetLook, GadgetLookInput, GadgetLookOptions, GadgetShared } from '~/composables/gadget/useGadgetContext.types';
import UtilGadget from '~/utils/util-gadget';

/**
 * 가젯들이 나눠 쓰는 문맥 — 화면(홈·가젯 페이지·게시판 레이아웃)이 만든 방문 상태(폭·지금 시각)와 템플릿이 정한 가젯 모양(`classic`·`card`).
 * 서버와 말하지 않는다(데이터는 페이지가 `useGadgetApi` 로 한 번 받아 템플릿에 내린다).
 * ⚠ setup 에서 부른다(`await` 뒤에는 provide/inject 문맥이 없다).
 */
const SHARED_KEY: InjectionKey<GadgetShared> = Symbol('gadget-shared');
const LOOK_KEY: InjectionKey<GadgetLook> = Symbol('gadget-look');
const LOOK_OPTIONS_KEY: InjectionKey<GadgetLookOptions> = Symbol('gadget-look-options');
const STACKED_KEY: InjectionKey<Readonly<Ref<boolean>>> = Symbol('gadget-stacked');
const BOARD_BODY_KEY: InjectionKey<() => VNode[] | undefined> = Symbol('board-layout-body');

/** 세부 모양 기본값 — 값을 주지 않는 템플릿의 모양(한 열도 지금 모양 그대로) */
const DEFAULT_LOOK_OPTIONS: Readonly<GadgetLookOptions> = {
    heroSplit: false,
    galleryColumns: 3,
    mobile: { order: 'reading', highlightScroll: false, heroBleed: false, galleryScroll: false, groupBuyScroll: false, groupsScroll: false, boardCardsScroll: false },
};
/** 홈 격자 밖(한 열을 모른다) — 늘 false */
const NOT_STACKED: Readonly<Ref<boolean>> = shallowRef(false);

function createShared(): GadgetShared {
    const wide = useMediaQuery(UtilGadget.WIDE_QUERY);
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
 * @returns {GadgetShared} 공유 상태.
 */
export function provideGadgetShared(): GadgetShared {
    const shared = createShared();
    provide(SHARED_KEY, shared);
    return shared;
}

/**
 * 템플릿·가젯이 부른다 — 홈이 만든 공유 상태(홈 밖이면 자기 것을 만든다).
 * @returns {GadgetShared} 공유 상태.
 */
export function useGadgetShared(): GadgetShared {
    return inject(SHARED_KEY, null) ?? createShared();
}

/**
 * 템플릿이 부른다 — 아래 가젯이 그릴 모양을 정한다.
 * @param {GadgetLook} look - `classic`(기본형) · `card`(카드형).
 * @param {GadgetLookInput} [options] - 세부 모양(선택) — 주지 않은 값은 기본값(한 열 모양 `mobile` 도 값마다).
 */
export function provideGadgetLook(look: GadgetLook, options?: GadgetLookInput): void {
    provide(LOOK_KEY, look);
    provide(LOOK_OPTIONS_KEY, { ...DEFAULT_LOOK_OPTIONS, ...options, mobile: { ...DEFAULT_LOOK_OPTIONS.mobile, ...options?.mobile } });
}

/**
 * 홈 격자(`GadgetGrid`)가 부른다 — 지금 한 열(`stack`)로 그리는지 아래 가젯에 알린다. 가젯은 이것과 템플릿의 한 열 모양(`mobile`)을 함께 보고 한 열 모양을 고른다(둘 다일 때만 — 데스크톱·태블릿 격자는 그대로).
 * @param {Readonly<Ref<boolean>>} stacked - 한 열인가.
 */
export function provideGadgetStacked(stacked: Readonly<Ref<boolean>>): void {
    provide(STACKED_KEY, stacked);
}

/**
 * 가젯이 부른다 — 홈 격자가 지금 한 열로 그리는가(격자 밖이면 늘 false).
 * @returns {Readonly<Ref<boolean>>} 한 열인가.
 */
export function useGadgetStacked(): Readonly<Ref<boolean>> {
    return inject(STACKED_KEY, NOT_STACKED);
}

/**
 * 가젯이 부른다 — 템플릿이 정한 모양(템플릿 밖이면 기본형).
 * @returns {GadgetLook} 모양.
 */
export function useGadgetLook(): GadgetLook {
    return inject(LOOK_KEY, 'classic');
}

/**
 * 가젯이 부른다 — 템플릿이 고른 세부 모양(템플릿 밖이거나 주지 않았으면 기본값).
 * @returns {GadgetLookOptions} 세부 모양.
 */
export function useGadgetLookOptions(): Readonly<GadgetLookOptions> {
    return inject(LOOK_OPTIONS_KEY, DEFAULT_LOOK_OPTIONS);
}

/**
 * 게시판 레이아웃 격자(`BoardLayoutGrid`)가 부른다 — 고정 칸 「게시판 본문」(`BOARD_BODY`) 자리에 그릴 내용(게시판 목록·글 보기 화면의 슬롯)을 아래 격자에 넘긴다.
 * 템플릿(테마 홈 겉모양)을 사이에 두고도 닿게 provide 로 넘긴다 — 슬롯 함수라 화면(페이지)의 상태를 그대로 따른다.
 * @param {() => VNode[] | undefined} render - 본문 그리기(보통 `slots.default`).
 */
export function provideBoardLayoutBody(render: () => VNode[] | undefined): void {
    provide(BOARD_BODY_KEY, render);
}

/**
 * 홈 격자가 부른다 — 게시판 본문 그리기(게시판 레이아웃 밖이면 null — 본문 칸을 그리지 않는다).
 * @returns {(() => VNode[] | undefined) | null} 본문 그리기.
 */
export function useBoardLayoutBody(): (() => VNode[] | undefined) | null {
    return inject(BOARD_BODY_KEY, null);
}
