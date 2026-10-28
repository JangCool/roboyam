import { defineAsyncComponent, type AsyncComponentLoader, type Component } from 'vue';
import UtilMenuPosition, { type MenuOverflow, type MenuPosition, type MenuStyle, type MenuWidth, type TopItemsPosition } from '~/utils/util-menu-position';

/**
 * 테마 레지스트리와 슬롯 찾기 규칙 — 규약은 `.claude/guides/frontend/themes.md`. 상태도 요청도 없다(현재 테마 값은 `useTheme`).
 *
 * 테마는 **바꾸는 슬롯만** 선언한다. 선언하지 않은 슬롯은 기본 테마 것을 쓴다.
 * 찾는 순서: ① 현재 테마의 `항목:하위축` → ② 현재 테마의 `항목` → ③ 기본 테마의 `항목:하위축` → ④ 기본 테마의 `항목`. 기본 테마는 모든 슬롯을 가져 화면이 깨지지 않는다.
 *
 * 슬롯은 필요할 때 하나씩 등록한다(지금은 셸 `shell` · 게시판 목록 `postList`(하위 축 = 목록 유형 `LIST`·`CARD`·`GALLERY`) · 홈 템플릿 `home`). 새 슬롯을 더하면 `ThemeSlotName` 과 기본 테마 선언, 같은 props 계약(`components/themes/` 의 `*.types.ts`)을 함께 정한다.
 * 테마 컴포넌트는 `defineAsyncComponent` 로 불러 쓰지 않는 테마의 코드가 번들에 들어가지 않게 한다 — 동기 import 하지 않는다.
 */

/**
 * 등록된 슬롯 항목
 * - `shell` 데스크톱 셸(헤더·메뉴 위치·본문 폭·푸터) — 계약 `components/themes/ThemeShell.types.ts`
 * - `postList` 게시판 목록 — 하위 축은 게시판이 정한 목록 유형(`LIST`·`CARD`·`GALLERY`, 보기 전환 포함). 테마는 유형을 정하지 않고 같은 유형의 생김새만 바꾼다. 계약 `components/board/BoardPostList.types.ts`
 * - `home` 공개 홈의 템플릿(칸 구성 — 템플릿 = 테마, 사용자 결정 2026-10-28) — 기본 테마 = 기본형, 다음식 = 다음식, 로보얌 = 로보얌형(`RoboyamHome`), 네이버식 = 네이버형. 칸 안의 가젯은 테마 공통 부품
 *   (`components/home/`, `UtilHomeGadgetRegistry`)이다. 계약 `components/themes/HomeTemplate.types.ts`
 */
export type ThemeSlotName = 'shell' | 'postList' | 'home';

/** 슬롯 컴포넌트를 불러오는 함수 */
type SlotLoader = AsyncComponentLoader<Component>;

/** 테마 선언 — 데스크톱 메뉴 위치의 기본값 + 바꾸는 슬롯만. 슬롯 키는 `항목` 또는 `항목:하위축` */
interface ThemeDeclaration {
    /** 메뉴 위치 기본값 — 운영자가 「테마 기본」, 회원이 「자동」일 때 쓴다(`UtilMenuPosition.resolve`). 각 위치의 모양은 그 테마의 셸이 정한다 */
    menuPosition: MenuPosition;
    /** 최상위 항목(홈·인기글 …) 위치 기본값 — 운영자가 「테마 기본」일 때 쓴다(`UtilMenuPosition.resolveTopItems`) */
    topItems: TopItemsPosition;
    /** 상단 메뉴 폭 기본값 — 운영자가 「테마 기본」일 때 쓴다(`UtilMenuPosition.resolveWidth`). 로보얌만 `FULL`(창 좌우 끝까지), 나머지는 `CONTENT`(본문 폭) */
    menuWidth: MenuWidth;
    /** 가로 메뉴 줄이 넘칠 때의 표시 기본값 — 운영자가 「테마 기본」일 때 쓴다(`UtilMenuPosition.resolveOverflow`). 지금은 모든 테마가 `SCROLLBAR`(줄 아래 얇은 스크롤) */
    menuOverflow: MenuOverflow;
    /**
     * 메뉴 모양 기본값(사용자 결정 2026-10-28 — 운영자가 칸마다 바꿀 수 있고, 「테마 기본」일 때 이 값을 쓴다: `UtilMenuPosition.resolveStyle`).
     * 바꾸는 칸만 적는다 — 적지 않은 칸은 `UtilMenuPosition.MENU_STYLE_FALLBACK`(TABS·COLUMN·NORMAL·PICKED·OFF·OFF·BRAND). 칸의 뜻은 `MENU_STYLE_VALUES`.
     */
    menuStyle?: Partial<MenuStyle>;
    slots: Partial<Record<string, SlotLoader>>;
}

/** 기본 목록형 — 하위 축 없는 `postList`(모르는 유형의 폴백)와 `postList:LIST` 가 같은 로더를 써서 비동기 컴포넌트도 하나다 */
const defaultPostList: SlotLoader = () => import('~/components/board/BoardPostList.vue');

/**
 * 다음식 슬롯 — 다음식(`daum`)과 로보얌 테마(`roboyam`)가 **같은 로더**를 쓴다(부품을 복사하지 않는다 — 같은 로더라 비동기 컴포넌트도 하나, `ASYNC_CACHE`).
 * 목록형만 헤드라인형 줄로(카드형·갤러리형은 기본 것 — 역할 변수·카드로 겉모양만 바뀐다), 홈은 다음식(카드 모듈 두 열: 본문 + 사이드 — 로보얌은 `home` 만 자기 템플릿으로 덮는다).
 */
const daumSlots: Partial<Record<string, SlotLoader>> = {
    shell: () => import('~/components/themes/daum/DaumShell.vue'),
    'postList:LIST': () => import('~/components/themes/daum/DaumPostList.vue'),
    home: () => import('~/components/themes/daum/DaumHome.vue'),
};

/** 테마 id → 선언. 새 테마는 id 와 슬롯을 한 줄씩 더한다 */
const THEMES = {
    default: {
        menuPosition: 'LEFT',
        topItems: 'MENU',
        menuWidth: 'CONTENT',
        menuOverflow: 'SCROLLBAR',
        slots: {
            shell: () => import('~/components/themes/default/DefaultShell.vue'),
            postList: defaultPostList,
            'postList:LIST': defaultPostList,
            'postList:CARD': () => import('~/components/board/BoardPostCards.vue'),
            'postList:GALLERY': () => import('~/components/board/BoardPostGallery.vue'),
            // 홈 — 기본형(본문 위 · 본문 좌우 · 오른쪽 사이드, 카드 없는 구역)
            home: () => import('~/components/themes/default/DefaultHome.vue'),
        },
    },
    /** 다음식 — 페이지 바탕 위 윗줄 + 흰 메뉴 카드(GNB·LNB), 왼쪽 메뉴 없음 */
    daum: {
        menuPosition: 'TOP',
        topItems: 'MENU',
        menuWidth: 'CONTENT',
        menuOverflow: 'SCROLLBAR',
        slots: daumSlots,
    },
    /**
     * 로보얌 테마(사용자 결정 2026-10-28) — 겉모양은 다음식 그대로(같은 슬롯 로더 `daumSlots` · `main.css` 의 다음식 값을 함께 쓴다), 기본 배치와 홈 템플릿(`RoboyamHome`)만 다르다:
     * 상단(흰 메뉴 카드 GNB·LNB — 상단 띠 그룹은 기본 폴백 = 첫 최상위 그룹) + 왼쪽 흰 카드 사이드(나머지 그룹), 최상위 항목(홈·인기글 …)은 GNB 맨 앞.
     * 메뉴 카드의 그룹은 둘째 줄에 「그룹 이름 + 칸」으로 놓는다(`menuStyle.menuGroupStyle: 'INLINE'`) — 첫 줄은 최상위 항목만.
     * 로보얌만 색·세부를 바꿀 때는 `main.css` 의 `html[data-theme='roboyam']` 자리에 덮어쓴다(메뉴 모양은 테마 이름이 아니라 해석된 설정값 — 루트 `data-mobile-header`·`data-sticky-menu`).
     */
    roboyam: {
        // 사이드 그룹(커뮤니티·소모임 …)은 첫 줄 탭으로 올리고 누른 그룹만 오른쪽 사이드에(`sideGroupStyle: 'PICK'`) — 메뉴 카드는 한 단계 작게(`menuSize: 'COMPACT'`)
        menuPosition: 'TOP_RIGHT',
        topItems: 'MENU',
        // 상단 메뉴 카드는 창 좌우 끝까지(사용자 결정 2026-10-28)
        menuWidth: 'FULL',
        menuOverflow: 'SCROLLBAR',
        // 메뉴 모양 기본값 — 운영자가 「테마 기본」이면 이 값(그룹은 둘째 줄 「이름 + 칸」 · 사이드 그룹은 탭으로 골라 보기 · 작은 메뉴 카드 · 모바일 메뉴 두 줄 · 메뉴 설명 줄 · 메뉴 붙이기 · 흰 모바일 상단 바)
        menuStyle: {
            menuGroupStyle: 'INLINE',
            sideGroupStyle: 'PICK',
            menuSize: 'COMPACT',
            mobileMenu: 'MENU',
            menuDescription: 'ON',
            stickyMenu: 'ON',
            mobileHeader: 'PLAIN',
        },
        // 셸·목록은 다음식 그대로, 홈만 로보얌 템플릿(간결한 두 열 — 사용자 결정 2026-10-28)
        slots: { ...daumSlots, home: () => import('~/components/themes/roboyam/RoboyamHome.vue') },
    },
    /** 네이버식(뉴스 언론사 홈) — 흰 윗줄 + 주 색상 탭 바, 왼쪽 메뉴 없음 */
    naver: {
        menuPosition: 'TOP',
        topItems: 'LOGO',
        menuWidth: 'CONTENT',
        menuOverflow: 'SCROLLBAR',
        slots: {
            shell: () => import('~/components/themes/naver/NaverShell.vue'),
            // 목록형만 기사형 줄로 — 카드형·갤러리형은 기본 것
            'postList:LIST': () => import('~/components/themes/naver/NaverPostList.vue'),
            // 홈 — 네이버형(바로가기 줄 → 하이라이트 행 → 게시판 카드 격자, 사이드)
            home: () => import('~/components/themes/naver/NaverHome.vue'),
        },
    },
} satisfies Record<string, ThemeDeclaration>;

/** 등록된 테마 id */
export type ThemeId = keyof typeof THEMES;

/** 같은 로더에서 만든 비동기 컴포넌트를 다시 쓰기 위한 캐시 — 계산이 돌 때마다 새로 만들면 셸이 다시 마운트된다 */
const ASYNC_CACHE = new Map<SlotLoader, Component>();

function asyncOf(loader: SlotLoader): Component {
    let component = ASYNC_CACHE.get(loader);
    if (!component) {
        component = defineAsyncComponent(loader);
        ASYNC_CACHE.set(loader, component);
    }
    return component;
}

export default class UtilTheme {
    /** 기본 테마 id — 값을 모르거나 모르는 테마가 올 때의 폴백 */
    static readonly DEFAULT_ID: ThemeId = 'default';

    /**
     * 등록된 테마 id 인가.
     * @param {unknown} value - 후보 값(서버 응답).
     * @returns {boolean} 등록된 id 면 true.
     */
    static isThemeId(value: unknown): value is ThemeId {
        return typeof value === 'string' && Object.hasOwn(THEMES, value);
    }

    /**
     * 서버가 준 값을 테마 id 로 — 모르는 값·빈 값이면 기본 테마.
     * @param {unknown} value - 후보 값(서버 응답).
     * @returns {ThemeId} 쓸 테마 id.
     */
    static normalize(value: unknown): ThemeId {
        return UtilTheme.isThemeId(value) ? value : UtilTheme.DEFAULT_ID;
    }

    /**
     * 테마의 메뉴 위치 기본값.
     * @param {ThemeId} themeId - 테마.
     * @returns {MenuPosition} 기본 위치(기본 테마 = LEFT, 다음식·네이버식 = TOP, 로보얌 = TOP_LEFT).
     */
    static menuDefault(themeId: ThemeId): MenuPosition {
        return THEMES[themeId].menuPosition;
    }

    /**
     * 테마의 최상위 항목 위치 기본값.
     * @param {ThemeId} themeId - 테마.
     * @returns {TopItemsPosition} 기본값(네이버식 = LOGO, 기본·다음식·로보얌 = MENU).
     */
    static topItemsDefault(themeId: ThemeId): TopItemsPosition {
        return THEMES[themeId].topItems;
    }

    /**
     * 테마의 상단 메뉴 폭 기본값.
     * @param {ThemeId} themeId - 테마.
     * @returns {MenuWidth} 기본값(로보얌 = FULL, 나머지 = CONTENT).
     */
    static menuWidthDefault(themeId: ThemeId): MenuWidth {
        return THEMES[themeId].menuWidth;
    }

    /**
     * 테마의 메뉴 넘침 표시 기본값.
     * @param {ThemeId} themeId - 테마.
     * @returns {MenuOverflow} 기본값(지금은 모두 SCROLLBAR).
     */
    static menuOverflowDefault(themeId: ThemeId): MenuOverflow {
        return THEMES[themeId].menuOverflow;
    }

    /**
     * 테마의 메뉴 모양 기본값 — 「테마 기본」 용도로만 쓴다(셸·부품은 해석된 값 `useMenuStyle` 을 읽는다).
     * @param {ThemeId} themeId - 테마.
     * @returns {MenuStyle} 선언한 칸 + 나머지는 `MENU_STYLE_FALLBACK`(로보얌 = INLINE·PICK·COMPACT·MENU·ON·ON·PLAIN, 그 밖 = TABS·COLUMN·NORMAL·PICKED·OFF·OFF·BRAND).
     */
    static menuStyleDefault(themeId: ThemeId): MenuStyle {
        const declaration: ThemeDeclaration = THEMES[themeId];
        return { ...UtilMenuPosition.MENU_STYLE_FALLBACK, ...declaration.menuStyle };
    }

    /**
     * 슬롯 컴포넌트를 찾는다(폴백 규칙은 위 주석).
     * @param {ThemeId} themeId - 현재 테마.
     * @param {ThemeSlotName} slot - 슬롯 항목.
     * @param {string} [sub] - 하위 축(예: 게시판 목록 유형). 슬롯 키는 `항목:하위축`.
     * @returns {Component} 비동기 컴포넌트.
     */
    static resolve(themeId: ThemeId, slot: ThemeSlotName, sub?: string): Component {
        const keys = sub ? [`${slot}:${sub}`, slot] : [slot];
        for (const id of [themeId, UtilTheme.DEFAULT_ID]) {
            const slots: Partial<Record<string, SlotLoader>> = THEMES[id].slots;
            for (const key of keys) {
                const loader = slots[key];
                if (loader) return asyncOf(loader);
            }
        }
        // 기본 테마는 모든 슬롯을 선언한다 — 여기에 오면 선언이 빠진 것이다
        throw new Error(`기본 테마에 슬롯이 없다: ${slot}`);
    }
}
