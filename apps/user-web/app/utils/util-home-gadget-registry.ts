import { defineAsyncComponent, markRaw, type AsyncComponentLoader, type Component } from 'vue';

/**
 * 홈 가젯 **레지스트리**(community `useHomeGadgetRegistry` — 「플러그형」) — 가젯 종류 키 → 그리는 컴포넌트. 상태도 요청도 없다.
 * 가젯 컴포넌트는 **테마와 무관한 공통 부품**이다(`components/home/`) — 겉모양 차이는 역할 변수·`[data-theme]` CSS 와 템플릿이 정하는 모양(`useHomeGadgetLook`)으로 낸다.
 * 템플릿(칸 구성)은 테마 슬롯 `home` 이다(`UtilTheme.resolve(theme, 'home')`).
 *
 * **새 종류 = 컴포넌트(`{ gadget }` 만 받는다) + 아래 한 줄**(+ 서버 종류 등록). **모르는 키는 건너뛴다**(`componentOf` 가 null) — 서버가 먼저 배포돼도 화면이 깨지지 않는다.
 * 비동기 컴포넌트라 이번 응답에 없는 종류의 코드는 내려받지 않는다.
 */
const LOADERS: Readonly<Record<string, AsyncComponentLoader<Component>>> = {
    HIGHLIGHT: () => import('~/components/home/HomeGadgetHighlight.vue'),
    POST_LIST: () => import('~/components/home/HomeGadgetPostList.vue'),
    BOARD_CARDS: () => import('~/components/home/HomeGadgetBoardCards.vue'),
    GALLERY: () => import('~/components/home/HomeGadgetGallery.vue'),
    GROUPS: () => import('~/components/home/HomeGadgetGroups.vue'),
    GROUP_BUY: () => import('~/components/home/HomeGadgetGroupBuy.vue'),
    QUICK_LINKS: () => import('~/components/home/HomeGadgetQuickLinks.vue'),
    NOTICE_TEXT: () => import('~/components/home/HomeGadgetNoticeText.vue'),
};

const REGISTRY: Readonly<Record<string, Component>> = Object.fromEntries(Object.entries(LOADERS).map(([key, loader]) => [key, markRaw(defineAsyncComponent(loader))]));

export default class UtilHomeGadgetRegistry {
    /**
     * 가젯 종류의 컴포넌트
     * @param {string} type - 가젯 종류 키.
     * @returns {Component | null} 컴포넌트. 모르는 종류는 null(그리지 않는다).
     */
    static componentOf(type: string): Component | null {
        return Object.hasOwn(REGISTRY, type) ? REGISTRY[type]! : null;
    }
}
