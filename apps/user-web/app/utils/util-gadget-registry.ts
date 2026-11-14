import { defineAsyncComponent, markRaw, type AsyncComponentLoader, type Component } from 'vue';
import UtilBootSplash from '~/utils/util-boot-splash';

/**
 * 가젯 **레지스트리**(community `useHomeGadgetRegistry` — 「플러그형」) — 가젯 종류 키 → 그리는 컴포넌트. 상태도 요청도 없다.
 * 가젯 컴포넌트는 **테마와 무관한 공통 부품**이다(`components/gadget/`) — 겉모양 차이는 역할 변수·`[data-theme]` CSS 와 템플릿이 정하는 모양(`useGadgetLook`)으로 낸다.
 * 템플릿(칸 구성)은 테마 슬롯 `home` 이다(`UtilTheme.resolve(theme, 'home')`).
 *
 * **새 종류 = 컴포넌트(`{ gadget }` 만 받는다) + 아래 한 줄**(+ 서버 종류 등록). **모르는 키는 건너뛴다**(`componentOf` 가 null) — 서버가 먼저 배포돼도 화면이 깨지지 않는다.
 * 비동기 컴포넌트라 이번 응답에 없는 종류의 코드는 내려받지 않는다.
 */
const LOADERS: Readonly<Record<string, AsyncComponentLoader<Component>>> = {
    HIGHLIGHT: () => import('~/components/gadget/GadgetHighlight.vue'),
    POST_LIST: () => import('~/components/gadget/GadgetPostList.vue'),
    BOARD_CARDS: () => import('~/components/gadget/GadgetBoardCards.vue'),
    GALLERY: () => import('~/components/gadget/GadgetGallery.vue'),
    GROUPS: () => import('~/components/gadget/GadgetGroups.vue'),
    GROUP_BUY: () => import('~/components/gadget/GadgetGroupBuy.vue'),
    // 회원 핫딜 1차 — 서버 `GadgetKinds` 에는 아직 없다(목업)
    HOT_DEALS: () => import('~/components/gadget/GadgetHotDeals.vue'),
    QUICK_LINKS: () => import('~/components/gadget/GadgetQuickLinks.vue'),
    NOTICE_TEXT: () => import('~/components/gadget/GadgetNoticeText.vue'),
    // 서버 `GadgetKinds` 에는 아직 없다(목업)
    AD: () => import('~/components/gadget/GadgetAd.vue'),
    AD_TEXT_LINE: () => import('~/components/gadget/GadgetAdTextLine.vue'),
    // 팁·강좌·사용기 /tips 배치에만(tips-contract 「/tips 레이아웃 편집」) — 데이터는 /tips 화면이 나눠 준다(`useTipsShared`)
    TIPS_HEADLINE: () => import('~/components/gadget/GadgetTipsHeadline.vue'),
    TIPS_LATEST: () => import('~/components/gadget/GadgetTipsLatest.vue'),
    TIPS_POPULAR: () => import('~/components/gadget/GadgetTipsPopular.vue'),
    TIPS_TAGS: () => import('~/components/gadget/GadgetTipsTags.vue'),
};

// 조각을 받는 동안은 첫 로딩 스플래시가 기다린다(`UtilBootSplash` — 데이터 요청과 가젯 조각 받기 사이에 스플래시가 걷히지 않게)
const REGISTRY: Readonly<Record<string, Component>> = Object.fromEntries(
    Object.entries(LOADERS).map(([key, loader]) => [key, markRaw(defineAsyncComponent(() => UtilBootSplash.track(loader())))]),
);

export default class UtilGadgetRegistry {
    /**
     * 가젯 종류들의 코드를 미리 받기 시작한다(첫 로딩 조각 사슬 줄이기, `plugins/spa-warmup.client.ts`) — 모르는 종류는 건너뛴다. 실패해도 조용히 넘어간다
     * @param {readonly string[]} types - 가젯 종류 키들.
     */
    static preload(types: readonly string[]): void {
        for (const type of new Set(types)) {
            const loader = LOADERS[type];
            if (loader) void UtilBootSplash.track(loader()).catch(() => undefined);
        }
    }

    /**
     * 가젯 종류의 컴포넌트
     * @param {string} type - 가젯 종류 키.
     * @returns {Component | null} 컴포넌트. 모르는 종류는 null(그리지 않는다).
     */
    static componentOf(type: string): Component | null {
        return Object.hasOwn(REGISTRY, type) ? REGISTRY[type]! : null;
    }
}
