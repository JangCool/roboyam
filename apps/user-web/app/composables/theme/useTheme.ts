import UtilMenuPosition, {
    type MenuStyleKey,
    type SiteMenuOverflowSetting,
    type SiteMenuSetting,
    type SiteMenuStyle,
    type SiteMenuWidthSetting,
    type SiteTopItemsSetting,
} from '~/utils/util-menu-position';
import UtilTheme, { type ThemeId } from '~/utils/util-theme';

/** 개발 미리보기로 고른 값을 이 탭에 기억하는 저장 키(sessionStorage) */
const PREVIEW_THEME_KEY = 'roboyam:theme-preview';
const PREVIEW_MENU_KEY = 'roboyam:menu-preview';
const PREVIEW_TOP_KEY = 'roboyam:top-items-preview';
const PREVIEW_BAR_KEY = 'roboyam:bar-groups-preview';
const PREVIEW_WIDTH_KEY = 'roboyam:menu-width-preview';
const PREVIEW_OVERFLOW_KEY = 'roboyam:menu-overflow-preview';

/** 메뉴 모양 칸 → 개발 미리보기 주소 이름(`?groups=tabs|inline|theme` …). 저장 키는 `roboyam:menu-style-<칸>-preview` */
const STYLE_PREVIEW_PARAMS: Record<MenuStyleKey, string> = {
    menuGroupStyle: 'groups',
    sideGroupStyle: 'sidegroups',
    menuSize: 'menusize',
    mobileMenu: 'mobilemenu',
    menuDescription: 'menudesc',
    stickyMenu: 'sticky',
    mobileHeader: 'mheader',
};

/**
 * 개발 모드 전용 미리보기 값 하나 — 주소에 `?<param>=<값>` 이 있고 읽을 수 있는 값이면 이 탭(sessionStorage)에 기억하고 쓴다. 다음 화면부터는 기억한 값을 쓴다.
 * 읽을 수 없는 값은 무시한다(기억도 바꾸지 않는다). 기억을 지우려면 탭을 닫는다. `import.meta.dev` 가 거짓인 운영 빌드에서는 코드째 빠진다.
 * @returns 미리볼 값. 없으면 null(서버 값을 쓴다).
 */
function devPreview<T>(param: string, storageKey: string, read: (value: string | null) => T | null, write: (value: T) => string = String): T | null {
    if (!import.meta.dev) return null;
    try {
        const fromUrl = read(new URLSearchParams(window.location.search).get(param));
        if (fromUrl !== null) {
            window.sessionStorage.setItem(storageKey, write(fromUrl));
            return fromUrl;
        }
        return read(window.sessionStorage.getItem(storageKey));
    } catch {
        // 저장소가 막혀 있다(사생활 보호 모드 등) — 미리보기 없이 서버 값을 쓴다
        return null;
    }
}

/** `?bar=id1,id2` — 최상위 그룹 ID 쉼표 목록. `?bar=first` 는 빈 목록(첫 그룹 폴백)이다. 메뉴에 없는 ID 뿐이어도 폴백한다(`UtilMenu.split`) */
function readBarPreview(value: string | null): string[] | null {
    if (value === null) return null;
    if (value.trim().toLowerCase() === 'first') return [];
    return UtilMenuPosition.readBarGroupIds(value.split(','));
}

/**
 * 사이트 테마(운영자가 고른 값 하나)와 운영자의 데스크톱 메뉴 배치 값을 읽는다 — 셸 레이아웃(`layouts/default.vue`)이 슬롯 컴포넌트를 고르고, 플러그인(`plugins/01.site-theme.client.ts`)이 루트 `data-theme` 에 붙인다.
 * 서버와 말하는 것은 `useThemeApi` 다. 테마는 사용자 개인이 바꾸지 않는다 → `.claude/guides/frontend/themes.md`. 배치의 최종 값(회원 > 운영자 > 테마 기본)은 `useMenuPosition` 이 정한다.
 *
 * - `undefined` = 아직 안 물어봄. 그동안 `theme` 은 기본 테마, `siteMenu`·`siteTopItems`·`siteMenuWidth`·`siteMenuOverflow` 는 `THEME`(테마 기본 따름), `siteBarGroupIds` 는 빈 목록이다.
 * - 못 받거나 모르는 값이어도 던지지 않고 기본값으로 둔다(앱을 막지 않는다).
 * - 개발 모드에서만 미리 본다: `?theme=<id>`(서버 `SiteThemes.IDS` 가 아직 `default` 하나 — `daum`·`naver`·`roboyam` 은 여기로 본다) · `?menu=top|left|right|top-left|top-right|theme` ·
 *   `?top=logo|menu|theme`(최상위 항목 위치) · `?bar=<그룹ID>,<그룹ID>`(상단 띠 그룹, `?bar=first` 는 첫 그룹 폴백) · `?width=content|full|theme`(상단 메뉴 폭) ·
 *   `?overflow=scrollbar|arrows|theme`(메뉴가 넘칠 때의 표시) · 메뉴 모양 `?groups=tabs|inline|theme` · `?sidegroups=column|pick|theme` · `?menusize=normal|compact|theme` ·
 *   `?mobilemenu=picked|menu|theme` · `?menudesc=on|off|theme` · `?sticky=on|off|theme` · `?mheader=brand|plain|theme`. 미리보기가 있어도 서버에는 물어보고, 미리보기 값이 그 칸만 대신한다.
 *
 * @returns `theme` 지금 테마 id(항상 값이 있다) · `siteMenu`·`siteTopItems`·`siteBarGroupIds`·`siteMenuWidth`·`siteMenuOverflow` 운영자 배치 값 · `siteMenuStyle` 운영자 메뉴 모양 값(해석은 `useMenuStyle`) ·
 *   `ensureLoaded()` 아직 안 받았으면 받는다(앱 시작 때 플러그인이 한 번 부른다).
 */
export function useTheme() {
    const api = useThemeApi();
    const state = useState<ThemeId | undefined>('site-theme', () => undefined);
    const menuState = useState<SiteMenuSetting | undefined>('site-menu-position', () => undefined);
    const topState = useState<SiteTopItemsSetting | undefined>('site-top-items', () => undefined);
    const barState = useState<string[] | undefined>('site-bar-groups', () => undefined);
    const theme = computed<ThemeId>(() => state.value ?? UtilTheme.DEFAULT_ID);
    const siteMenu = computed<SiteMenuSetting>(() => menuState.value ?? UtilMenuPosition.SITE_DEFAULT);
    const siteTopItems = computed<SiteTopItemsSetting>(() => topState.value ?? UtilMenuPosition.SITE_TOP_ITEMS_DEFAULT);
    const siteBarGroupIds = computed<readonly string[]>(() => barState.value ?? []);
    const widthState = useState<SiteMenuWidthSetting | undefined>('site-menu-width', () => undefined);
    const siteMenuWidth = computed<SiteMenuWidthSetting>(() => widthState.value ?? UtilMenuPosition.SITE_WIDTH_DEFAULT);
    const overflowState = useState<SiteMenuOverflowSetting | undefined>('site-menu-overflow', () => undefined);
    const siteMenuOverflow = computed<SiteMenuOverflowSetting>(() => overflowState.value ?? UtilMenuPosition.SITE_OVERFLOW_DEFAULT);
    const styleState = useState<SiteMenuStyle | undefined>('site-menu-style', () => undefined);
    const siteMenuStyle = computed<SiteMenuStyle>(() => styleState.value ?? UtilMenuPosition.SITE_MENU_STYLE_DEFAULT);

    async function ensureLoaded(): Promise<void> {
        if (state.value !== undefined) return;
        let serverTheme: ThemeId = UtilTheme.DEFAULT_ID;
        let serverMenu: SiteMenuSetting = UtilMenuPosition.SITE_DEFAULT;
        let serverTop: SiteTopItemsSetting = UtilMenuPosition.SITE_TOP_ITEMS_DEFAULT;
        let serverBar: string[] = [];
        let serverWidth: SiteMenuWidthSetting = UtilMenuPosition.SITE_WIDTH_DEFAULT;
        let serverOverflow: SiteMenuOverflowSetting = UtilMenuPosition.SITE_OVERFLOW_DEFAULT;
        let serverStyle: SiteMenuStyle = { ...UtilMenuPosition.SITE_MENU_STYLE_DEFAULT };
        try {
            const res = await api.current();
            serverTheme = UtilTheme.normalize(res.theme);
            serverMenu = UtilMenuPosition.readSite(res.menuPosition);
            serverTop = UtilMenuPosition.readSiteTopItems(res.topItems);
            serverBar = UtilMenuPosition.readBarGroupIds(res.barGroupIds);
            serverWidth = UtilMenuPosition.readSiteWidth(res.menuWidth);
            serverOverflow = UtilMenuPosition.readSiteOverflow(res.menuOverflow);
            serverStyle = UtilMenuPosition.readSiteStyle(res);
        } catch {
            // 못 받았다 — 기본값으로 둔다
        }
        if (import.meta.dev) {
            state.value = devPreview('theme', PREVIEW_THEME_KEY, (v) => (UtilTheme.isThemeId(v) ? v : null)) ?? serverTheme;
            menuState.value = devPreview('menu', PREVIEW_MENU_KEY, UtilMenuPosition.fromPreview) ?? serverMenu;
            topState.value = devPreview('top', PREVIEW_TOP_KEY, UtilMenuPosition.topItemsFromPreview) ?? serverTop;
            barState.value = devPreview('bar', PREVIEW_BAR_KEY, readBarPreview, (ids) => (ids.length ? ids.join(',') : 'first')) ?? serverBar;
            widthState.value = devPreview('width', PREVIEW_WIDTH_KEY, UtilMenuPosition.widthFromPreview) ?? serverWidth;
            overflowState.value = devPreview('overflow', PREVIEW_OVERFLOW_KEY, UtilMenuPosition.overflowFromPreview) ?? serverOverflow;
            const style = { ...serverStyle } as Record<MenuStyleKey, string>;
            for (const key of UtilMenuPosition.styleKeys()) {
                const param = STYLE_PREVIEW_PARAMS[key];
                style[key] = devPreview(param, `roboyam:menu-style-${param}-preview`, (v) => UtilMenuPosition.readStyleValue(key, v)) ?? serverStyle[key];
            }
            styleState.value = style as SiteMenuStyle;
            return;
        }
        state.value = serverTheme;
        menuState.value = serverMenu;
        topState.value = serverTop;
        barState.value = serverBar;
        widthState.value = serverWidth;
        overflowState.value = serverOverflow;
        styleState.value = serverStyle;
    }

    return { theme, siteMenu, siteTopItems, siteBarGroupIds, siteMenuWidth, siteMenuOverflow, siteMenuStyle, ensureLoaded };
}
