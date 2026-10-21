import { defineAsyncComponent, type AsyncComponentLoader, type Component } from 'vue';

/**
 * 테마 레지스트리와 슬롯 찾기 규칙 — 규약은 `.claude/guides/frontend/themes.md`. 상태도 요청도 없다(현재 테마 값은 `useTheme`).
 *
 * 테마는 **바꾸는 슬롯만** 선언한다. 선언하지 않은 슬롯은 기본 테마 것을 쓴다.
 * 찾는 순서: ① 현재 테마의 `항목:하위축` → ② 현재 테마의 `항목` → ③ 기본 테마의 `항목:하위축` → ④ 기본 테마의 `항목`. 기본 테마는 모든 슬롯을 가져 화면이 깨지지 않는다.
 *
 * 슬롯은 필요할 때 하나씩 등록한다(지금은 셸 `shell` 뿐). 새 슬롯을 더하면 `ThemeSlotName` 과 기본 테마 선언, 같은 props 계약(`components/themes/` 의 `*.types.ts`)을 함께 정한다.
 * 테마 컴포넌트는 `defineAsyncComponent` 로 불러 쓰지 않는 테마의 코드가 번들에 들어가지 않게 한다 — 동기 import 하지 않는다.
 */

/** 등록된 슬롯 항목 — 지금은 데스크톱 셸(헤더·메뉴 위치·본문 폭·푸터) 하나 */
export type ThemeSlotName = 'shell';

/** 슬롯 컴포넌트를 불러오는 함수 */
type SlotLoader = AsyncComponentLoader<Component>;

/** 테마 선언 — 바꾸는 슬롯만 적는다. 키는 `항목` 또는 `항목:하위축` */
interface ThemeDeclaration {
    slots: Partial<Record<string, SlotLoader>>;
}

/** 테마 id → 선언. 새 테마는 id 와 슬롯을 한 줄씩 더한다 */
const THEMES = {
    default: {
        slots: {
            shell: () => import('~/components/themes/default/DefaultShell.vue'),
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
