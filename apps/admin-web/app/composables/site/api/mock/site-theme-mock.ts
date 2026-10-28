// MOCK: 공개 사이트 테마와 테마별 메뉴 설정 목업(메뉴 설정은 테마마다 따로 — 사용자 결정 2026-10-28) — 서버 `GET/PUT /api/admin/site/theme` 에 아직 칸이 없다.
//   메뉴 위치(`menuPosition`) · 최상위 항목 위치(`topItems`) · 상단 띠 그룹(`barGroupIds`) · 상단 메뉴 폭(`menuWidth`) · 메뉴가 넘칠 때(`menuOverflow`)
//   · 메뉴 모양 7칸(`menuStyle` — 그룹 표시·사이드 그룹·메뉴 크기·모바일 상단 메뉴·메뉴 설명 줄·메뉴 붙이기·모바일 상단 바 모양)
//   · 테마별 기본값(`themeMenuDefaults`·`themeTopItemsDefaults`·`themeMenuWidthDefaults`·`themeMenuOverflowDefaults`·`themeMenuStyleDefaults`)
//   · 서버 `SiteThemes.IDS` 에 아직 없는 테마(`MOCK_ONLY_THEMES` — 서버는 아직 `default` 하나만 알아서 다음식 `daum` · 네이버식 `naver` · 로보얌 `roboyam` 셋)를 고를 수 있는 목록 끝에 덧붙이고, 그 테마를 고른 저장은 서버에 보내지 않고 여기에만 둔다.
// `useSiteSettingsApi.ts` 가 실제 응답에 이 값을 덧붙인다. 메모리에만 있어 새로고침하면 처음 값(테마 기본 따름·빈 목록·서버 테마)으로 돌아가고, 공개 사이트에는 전해지지 않는다
// (공개 사이트는 개발 미리보기 `?theme=`·`?menu=`·`?top=`·`?bar=`·`?width=`·`?overflow=`·`?groups=`·`?sidegroups=`·`?menusize=`·`?mobilemenu=`·`?menudesc=`·`?sticky=`·`?mheader=` 로 본다).
// 서버가 칸·테마를 주면 이 파일을 지운다.

import type {
    SiteMenuOverflow,
    SiteMenuStyle,
    SiteMenuStyleSetting,
    SiteMenuWidth,
    SiteThemeMenuPosition,
    SiteThemeResponse,
    SiteTopItemsPosition,
} from '~/composables/site/api/useSiteSettingsApi.types';

/** 테마별 기본값 — user-web `util-theme.ts` 의 `THEMES[*].menuPosition`·`topItems`·`menuWidth`·`menuOverflow` 와 같은 값(서버가 주게 되면 원천은 서버 `SiteThemes`) */
const THEME_MENU_DEFAULTS: Record<string, SiteThemeMenuPosition> = { default: 'LEFT', daum: 'TOP', naver: 'TOP', roboyam: 'TOP_RIGHT' };
const THEME_TOP_ITEMS_DEFAULTS: Record<string, SiteTopItemsPosition> = { default: 'MENU', daum: 'MENU', naver: 'LOGO', roboyam: 'MENU' };
const THEME_MENU_WIDTH_DEFAULTS: Record<string, SiteMenuWidth> = { default: 'CONTENT', daum: 'CONTENT', naver: 'CONTENT', roboyam: 'FULL' };
const THEME_MENU_OVERFLOW_DEFAULTS: Record<string, SiteMenuOverflow> = { default: 'SCROLLBAR', daum: 'SCROLLBAR', naver: 'SCROLLBAR', roboyam: 'SCROLLBAR' };
/** 메뉴 모양 기본값 — user-web `UtilTheme.menuStyleDefault`(`THEMES[*].menuStyle` + `MENU_STYLE_FALLBACK`)와 같은 값 */
const BASE_MENU_STYLE: SiteMenuStyle = {
    menuGroupStyle: 'TABS',
    sideGroupStyle: 'COLUMN',
    menuSize: 'NORMAL',
    mobileMenu: 'PICKED',
    menuDescription: 'OFF',
    stickyMenu: 'OFF',
    mobileHeader: 'BRAND',
};
const THEME_MENU_STYLE_DEFAULTS: Record<string, SiteMenuStyle> = {
    default: BASE_MENU_STYLE,
    daum: BASE_MENU_STYLE,
    naver: BASE_MENU_STYLE,
    roboyam: { menuGroupStyle: 'INLINE', sideGroupStyle: 'PICK', menuSize: 'COMPACT', mobileMenu: 'MENU', menuDescription: 'ON', stickyMenu: 'ON', mobileHeader: 'PLAIN' },
};
/** 운영자 메뉴 모양의 처음 값 — 칸마다 테마 기본 따름 */
const THEME_STYLE: SiteMenuStyleSetting = {
    menuGroupStyle: 'THEME',
    sideGroupStyle: 'THEME',
    menuSize: 'THEME',
    mobileMenu: 'THEME',
    menuDescription: 'THEME',
    stickyMenu: 'THEME',
    mobileHeader: 'THEME',
};

/** 서버 `SiteThemes.IDS` 에 아직 없지만 화면에서 고를 수 있게 둘 테마 id(보일 순서대로 목록 끝에) */
const MOCK_ONLY_THEMES: readonly string[] = ['daum', 'naver', 'roboyam'];

/** 서버가 지금 주는 칸 */
export type SiteThemeServerFields = Pick<SiteThemeResponse, 'theme' | 'themes'>;

/** 테마 하나의 메뉴 설정(운영자 값) — 테마마다 따로 둔다(사용자 결정 2026-10-28: 메뉴 설정·홈 구성은 테마 아래) */
export type SiteThemeMenuFields = Pick<SiteThemeResponse, 'menuPosition' | 'topItems' | 'barGroupIds' | 'menuWidth' | 'menuOverflow' | 'menuStyle'>;

/** 처음 값 — 칸마다 테마 기본 따름 · 빈 상단 띠 그룹 */
const INITIAL_MENU: SiteThemeMenuFields = { menuPosition: 'THEME', topItems: 'THEME', barGroupIds: [], menuWidth: 'THEME', menuOverflow: 'THEME', menuStyle: { ...THEME_STYLE } };

/** 저장한 사이트 테마 — 목업 전용 테마를 골랐을 때만 값이 있다(그 밖에는 서버 값을 쓴다) */
let savedTheme: string | null = null;
/** 테마 id → 저장한 메뉴 설정(없으면 처음 값) */
const savedMenus: Record<string, SiteThemeMenuFields> = {};
/** 마지막으로 받은 서버 테마 목록 — 서버가 그 테마를 알게 되면 목업 전용으로 보지 않는다 */
let serverThemes: string[] = [];

function copyMenu(menu: SiteThemeMenuFields): SiteThemeMenuFields {
    return { ...menu, barGroupIds: [...menu.barGroupIds], menuStyle: { ...menu.menuStyle } };
}

/**
 * 저장할 테마가 목업 전용(서버 목록에 없는 `MOCK_ONLY_THEMES`)인가 — 그렇다면 서버 `PUT` 에 보내지 않는다(목록 밖 id 는 400).
 * @param {string} id - 고른 테마 id.
 * @returns {boolean} 목업 전용이면 true.
 */
export function isMockOnlyTheme(id: string): boolean {
    return MOCK_ONLY_THEMES.includes(id) && !serverThemes.includes(id);
}

/**
 * 서버 응답에 목업 칸을 덧붙인다 — 목업 전용 테마를 목록 끝에 더하고, 그 테마를 저장해 두었으면 지금 테마로 보인다.
 * 메뉴 칸은 `forTheme`(없으면 지금 테마)의 메뉴 설정이다 — 공개 사이트는 지금 테마의 것을 쓴다.
 * @param {SiteThemeServerFields} server - 서버가 준 `{ theme, themes }`.
 * @param {string} [forTheme] - 메뉴 설정을 볼 테마 id.
 * @returns {SiteThemeResponse} 화면이 쓰는 전체 모양.
 */
export function mockThemeMenu(server: SiteThemeServerFields, forTheme?: string): SiteThemeResponse {
    serverThemes = [...server.themes];
    const themes = [...server.themes, ...MOCK_ONLY_THEMES.filter((id) => !server.themes.includes(id))];
    const theme = savedTheme !== null && isMockOnlyTheme(savedTheme) ? savedTheme : server.theme;
    const menu = copyMenu(savedMenus[forTheme ?? theme] ?? INITIAL_MENU);
    return {
        theme,
        themes,
        ...menu,
        themeMenuDefaults: { ...THEME_MENU_DEFAULTS },
        themeTopItemsDefaults: { ...THEME_TOP_ITEMS_DEFAULTS },
        themeMenuWidthDefaults: { ...THEME_MENU_WIDTH_DEFAULTS },
        themeMenuOverflowDefaults: { ...THEME_MENU_OVERFLOW_DEFAULTS },
        themeMenuStyleDefaults: Object.fromEntries(Object.entries(THEME_MENU_STYLE_DEFAULTS).map(([id, style]) => [id, { ...style }])),
    };
}

/**
 * 사이트 테마를 저장한 것으로 둔다 — 서버 테마를 고르면 목업 테마는 지운다. 메뉴 설정은 건드리지 않는다(테마마다 따로).
 * @param {string} theme - 고른 테마 id.
 */
export function mockSaveTheme(theme: string): void {
    savedTheme = isMockOnlyTheme(theme) ? theme : null;
}

/**
 * 테마 하나의 메뉴 설정을 저장한 것으로 둔다.
 * @param {string} theme - 테마 id.
 * @param {SiteThemeMenuFields} menu - 메뉴 설정.
 */
export function mockSaveThemeMenu(theme: string, menu: SiteThemeMenuFields): void {
    savedMenus[theme] = copyMenu(menu);
}
