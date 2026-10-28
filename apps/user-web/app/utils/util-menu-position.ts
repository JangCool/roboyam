/**
 * 데스크톱 메뉴 배치 규칙 — 메뉴 위치와 최상위 항목 위치를 **한 곳에서** 정한다. 상태도 요청도 없다(공유 상태 연결은 `useMenuPosition`).
 *
 * 메뉴 위치(사용자 결정 2026-10-28): **회원 개인 선택(보기 설정) > 운영자 설정(사이트 테마 구획) > 테마 기본값(`UtilTheme.menuDefault`)**.
 *  - 값: `TOP` 상단 · `LEFT`/`RIGHT` 사이드 · `TOP_LEFT`/`TOP_RIGHT` 상단 + 사이드(섞인 배치 — 운영자가 고른 그룹(`barGroupIds`)은 상단, 나머지는 사이드).
 *  - 회원 값 `AUTO` 는 「사이트 설정 따름」, 운영자 값 `THEME` 은 「테마 기본 따름」이다.
 * 최상위 항목 위치(운영자만, 회원은 고르지 않는다): `LOGO` 로고 옆 작은 링크 줄 · `MENU` 메뉴 안. 운영자 `THEME` 이면 테마 기본값(`UtilTheme.topItemsDefault`).
 * 상단 메뉴 폭(운영자만, 사용자 결정 2026-10-28): `CONTENT` 본문 폭 · `FULL` 화면 좌우 끝까지(메뉴 상자의 바탕·테두리만 늘고 항목 시작은 본문 열 왼쪽 끝 그대로).
 *   운영자 `THEME` 이면 테마 기본값(`UtilTheme.menuWidthDefault`, 지금은 모두 `CONTENT`). 상단 메뉴가 없는 배치(`LEFT`·`RIGHT`)에서는 셸이 쓰지 않는다.
 * 메뉴가 넘칠 때(운영자만, 사용자 결정 2026-10-28): `SCROLLBAR` 줄 아래 얇은 스크롤 표시 · `ARROWS` 줄 양끝 화살표 단추 — 가로 메뉴 줄(`AppScrollRow`)이 넘칠 때만 보인다.
 *   운영자 `THEME` 이면 테마 기본값(`UtilTheme.menuOverflowDefault`, 지금은 모두 `SCROLLBAR`). 모바일(md 미만)은 손가락 밀기만이라 표시가 없다.
 * 메뉴 모양(운영자만, 사용자 결정 2026-10-28 — `MENU_STYLE_VALUES` 7칸): 칸마다 운영자 `THEME` 이면 테마 기본값(`UtilTheme.menuStyleDefault`). 해석은 `resolveStyle`, 공유 상태 연결은 `useMenuStyle`.
 *   모바일 상단 메뉴·메뉴 설명·메뉴 붙이기·모바일 상단 바 모양은 모바일에도 쓰인다.
 * 위치·폭·넘침은 모바일(md 미만)과 무관하다(하단 탭·드로어·상단 탭 — 테마 공통). 테마는 기본값과 **각 배치에서의 모양**만 정한다(`.claude/guides/frontend/themes.md`).
 * 메뉴 트리를 로고 옆·상단·사이드로 나누는 규칙은 `UtilMenu.split` 이다(이 파일의 `barScope` 가 그 입력을 만든다).
 */

/** 메뉴 위치 — 상단 · 사이드 왼쪽 · 사이드 오른쪽 · 상단+사이드 왼쪽 · 상단+사이드 오른쪽 */
export type MenuPosition = 'TOP' | 'LEFT' | 'RIGHT' | 'TOP_LEFT' | 'TOP_RIGHT';

/** 운영자 설정 값 — `THEME` 은 테마 기본값을 따른다 */
export type SiteMenuSetting = 'THEME' | MenuPosition;

/** 회원 보기 설정 값 — `AUTO` 는 사이트(운영자·테마) 값을 따른다 */
export type MemberMenuSetting = 'AUTO' | MenuPosition;

/** 최상위 항목(제목 없는 구역 — 홈·인기글 …) 위치 — 로고 옆 · 메뉴 안 */
export type TopItemsPosition = 'LOGO' | 'MENU';

/** 운영자의 최상위 항목 위치 값 — `THEME` 은 테마 기본값을 따른다 */
export type SiteTopItemsSetting = 'THEME' | TopItemsPosition;

/** 상단 메뉴(상단 막대·메뉴 카드·탭 바) 폭 — 본문 폭 · 화면 전체 */
export type MenuWidth = 'CONTENT' | 'FULL';

/** 운영자의 상단 메뉴 폭 값 — `THEME` 은 테마 기본값을 따른다(회원은 고르지 않는다) */
export type SiteMenuWidthSetting = 'THEME' | MenuWidth;

/** 가로 메뉴 줄이 넘칠 때 보일 것 — 줄 아래 얇은 스크롤 표시 · 줄 양끝 화살표 단추 */
export type MenuOverflow = 'SCROLLBAR' | 'ARROWS';

/** 운영자의 메뉴 넘침 값 — `THEME` 은 테마 기본값을 따른다(회원은 고르지 않는다) */
export type SiteMenuOverflowSetting = 'THEME' | MenuOverflow;

/** 상단 막대에 올릴 범위 — 모든 그룹 · 없음(사이드만) · 고른 최상위 그룹 ID(섞인 배치) */
export type MenuBarScope = 'ALL' | 'NONE' | readonly string[];

/**
 * 메뉴 모양 설정(운영자 값, 사용자 결정 2026-10-28 — 예전에는 테마 선언에 고정) — 칸 이름 → 고를 수 있는 값(첫 값이 테마 선언이 없을 때의 기본).
 *  - `menuGroupStyle` 상단 메뉴 그룹 표시: `TABS` 첫 줄 그룹 이름 탭 → 누르면 둘째 줄 · `INLINE` 첫 줄은 최상위 항목, 둘째 줄에 「그룹 이름 + 칸」(다음식 셸 `DaumMenuBar`)
 *  - `sideGroupStyle` 섞인 배치의 사이드 그룹: `COLUMN` 사이드에 늘 모두 · `PICK` 첫 줄 탭으로 올려 누른 그룹만 사이드(다음식 셸, `useSideGroupPick`)
 *  - `menuSize` 상단 메뉴 크기: `NORMAL` · `COMPACT` 한 단계 작게(다음식 셸 메뉴 카드)
 *  - `mobileMenu` 모바일 상단 메뉴: `PICKED` 운영자가 메뉴 구성에서 고른 모바일 탭 한 줄 · `MENU` 메뉴 구조 두 줄(`AppMobileMenuRows`)
 *  - `menuDescription` 메뉴 설명 줄: `OFF` · `ON` 최상위 항목(인기글·최신글·공지 …)에 있을 때 둘째 줄에 그 메뉴 설명(`useMenuDescription` — `DaumMenuBar`·`AppMobileMenuRows`)
 *  - `stickyMenu` 메뉴 붙이기: `OFF` · `ON` 본문을 내리면 로고 줄은 사라지고 메뉴 줄이 창 위에 붙는다(다음식 셸 데스크톱 · 모바일 상단 탭 — 루트 `data-sticky-menu`)
 *  - `mobileHeader` 모바일 상단 바 모양: `BRAND` 주 색상 채움 · `PLAIN` 흰 바 + 흰 메뉴 띠, 굵은 탭·검은 밑줄(루트 `data-mobile-header`)
 */
export const MENU_STYLE_VALUES = {
    menuGroupStyle: ['TABS', 'INLINE'],
    sideGroupStyle: ['COLUMN', 'PICK'],
    menuSize: ['NORMAL', 'COMPACT'],
    mobileMenu: ['PICKED', 'MENU'],
    menuDescription: ['OFF', 'ON'],
    stickyMenu: ['OFF', 'ON'],
    mobileHeader: ['BRAND', 'PLAIN'],
} as const;

/** 메뉴 모양 칸 이름 */
export type MenuStyleKey = keyof typeof MENU_STYLE_VALUES;

/** 해석된 메뉴 모양(셸·부품이 쓰는 값) */
export type MenuStyle = { [K in MenuStyleKey]: (typeof MENU_STYLE_VALUES)[K][number] };

/** 운영자의 메뉴 모양 값 — 칸마다 `THEME` 은 테마 기본값을 따른다(회원은 고르지 않는다) */
export type SiteMenuStyle = { [K in MenuStyleKey]: 'THEME' | MenuStyle[K] };

/** 메뉴 모양 칸 목록(차례 고정) */
const MENU_STYLE_KEYS = Object.keys(MENU_STYLE_VALUES) as MenuStyleKey[];

export default class UtilMenuPosition {
    static readonly POSITIONS: readonly MenuPosition[] = ['TOP', 'LEFT', 'RIGHT', 'TOP_LEFT', 'TOP_RIGHT'];
    static readonly SITE_SETTINGS: readonly SiteMenuSetting[] = ['THEME', ...UtilMenuPosition.POSITIONS];
    static readonly MEMBER_SETTINGS: readonly MemberMenuSetting[] = ['AUTO', ...UtilMenuPosition.POSITIONS];
    static readonly SITE_TOP_ITEMS: readonly SiteTopItemsSetting[] = ['THEME', 'LOGO', 'MENU'];
    static readonly SITE_WIDTHS: readonly SiteMenuWidthSetting[] = ['THEME', 'CONTENT', 'FULL'];
    static readonly SITE_OVERFLOWS: readonly SiteMenuOverflowSetting[] = ['THEME', 'SCROLLBAR', 'ARROWS'];

    /** 운영자 값의 기본 — 테마 기본 따름 */
    static readonly SITE_DEFAULT: SiteMenuSetting = 'THEME';
    /** 회원 값의 기본 — 사이트 설정 따름 */
    static readonly MEMBER_DEFAULT: MemberMenuSetting = 'AUTO';
    /** 운영자 최상위 항목 위치의 기본 — 테마 기본 따름 */
    static readonly SITE_TOP_ITEMS_DEFAULT: SiteTopItemsSetting = 'THEME';
    /** 운영자 상단 메뉴 폭의 기본 — 테마 기본 따름 */
    static readonly SITE_WIDTH_DEFAULT: SiteMenuWidthSetting = 'THEME';
    /** 운영자 메뉴 넘침 값의 기본 — 테마 기본 따름 */
    static readonly SITE_OVERFLOW_DEFAULT: SiteMenuOverflowSetting = 'THEME';

    /**
     * 지금 쓸 메뉴 위치 — 회원 값(AUTO 가 아니면) → 운영자 값(THEME 이 아니면) → 테마 기본값.
     * @param {MemberMenuSetting} member - 회원 보기 설정 값.
     * @param {SiteMenuSetting} site - 운영자 설정 값.
     * @param {MenuPosition} themeDefault - 현재 테마의 기본 위치.
     * @returns {MenuPosition} 메뉴 위치.
     */
    static resolve(member: MemberMenuSetting, site: SiteMenuSetting, themeDefault: MenuPosition): MenuPosition {
        if (member !== 'AUTO') return member;
        if (site !== 'THEME') return site;
        return themeDefault;
    }

    /**
     * 지금 쓸 최상위 항목 위치 — 운영자 값(THEME 이 아니면) → 테마 기본값.
     * @param {SiteTopItemsSetting} site - 운영자 설정 값.
     * @param {TopItemsPosition} themeDefault - 현재 테마의 기본값.
     * @returns {TopItemsPosition} 최상위 항목 위치.
     */
    static resolveTopItems(site: SiteTopItemsSetting, themeDefault: TopItemsPosition): TopItemsPosition {
        return site === 'THEME' ? themeDefault : site;
    }

    /**
     * 지금 쓸 상단 메뉴 폭 — 운영자 값(THEME 이 아니면) → 테마 기본값.
     * @param {SiteMenuWidthSetting} site - 운영자 설정 값.
     * @param {MenuWidth} themeDefault - 현재 테마의 기본값.
     * @returns {MenuWidth} 상단 메뉴 폭.
     */
    static resolveWidth(site: SiteMenuWidthSetting, themeDefault: MenuWidth): MenuWidth {
        return site === 'THEME' ? themeDefault : site;
    }

    /**
     * 지금 쓸 메뉴 넘침 표시 — 운영자 값(THEME 이 아니면) → 테마 기본값.
     * @param {SiteMenuOverflowSetting} site - 운영자 설정 값.
     * @param {MenuOverflow} themeDefault - 현재 테마의 기본값.
     * @returns {MenuOverflow} 넘침 표시.
     */
    static resolveOverflow(site: SiteMenuOverflowSetting, themeDefault: MenuOverflow): MenuOverflow {
        return site === 'THEME' ? themeDefault : site;
    }

    /**
     * 운영자 값을 읽는다(서버 응답) — 없거나 모르는 값이면 `THEME`.
     * @param {unknown} value - 후보 값.
     * @returns {SiteMenuSetting} 운영자 값.
     */
    static readSite(value: unknown): SiteMenuSetting {
        return UtilMenuPosition.SITE_SETTINGS.find((v) => v === value) ?? UtilMenuPosition.SITE_DEFAULT;
    }

    /**
     * 회원 값을 읽는다(서버 응답·이 브라우저 사본) — 없거나 모르는 값이면 `AUTO`.
     * @param {unknown} value - 후보 값.
     * @returns {MemberMenuSetting} 회원 값.
     */
    static readMember(value: unknown): MemberMenuSetting {
        return UtilMenuPosition.MEMBER_SETTINGS.find((v) => v === value) ?? UtilMenuPosition.MEMBER_DEFAULT;
    }

    /**
     * 운영자의 최상위 항목 위치를 읽는다 — 없거나 모르는 값이면 `THEME`.
     * @param {unknown} value - 후보 값.
     * @returns {SiteTopItemsSetting} 운영자 값.
     */
    static readSiteTopItems(value: unknown): SiteTopItemsSetting {
        return UtilMenuPosition.SITE_TOP_ITEMS.find((v) => v === value) ?? UtilMenuPosition.SITE_TOP_ITEMS_DEFAULT;
    }

    /**
     * 운영자의 상단 메뉴 폭을 읽는다 — 없거나 모르는 값이면 `THEME`.
     * @param {unknown} value - 후보 값.
     * @returns {SiteMenuWidthSetting} 운영자 값.
     */
    static readSiteWidth(value: unknown): SiteMenuWidthSetting {
        return UtilMenuPosition.SITE_WIDTHS.find((v) => v === value) ?? UtilMenuPosition.SITE_WIDTH_DEFAULT;
    }

    /**
     * 운영자의 메뉴 넘침 값을 읽는다 — 없거나 모르는 값이면 `THEME`.
     * @param {unknown} value - 후보 값.
     * @returns {SiteMenuOverflowSetting} 운영자 값.
     */
    static readSiteOverflow(value: unknown): SiteMenuOverflowSetting {
        return UtilMenuPosition.SITE_OVERFLOWS.find((v) => v === value) ?? UtilMenuPosition.SITE_OVERFLOW_DEFAULT;
    }

    /**
     * 운영자가 상단 띠에 고른 그룹 ID 목록을 읽는다 — 글자만, 빈 글자·같은 ID 는 뺀다. 배열이 아니면 빈 목록(첫 그룹 폴백).
     * @param {unknown} value - 후보 값.
     * @returns {string[]} 그룹 ID 목록.
     */
    static readBarGroupIds(value: unknown): string[] {
        if (!Array.isArray(value)) return [];
        return [...new Set(value.filter((id): id is string => typeof id === 'string' && id.trim() !== '').map((id) => id.trim()))];
    }

    /**
     * 개발 미리보기 주소 값(`?menu=top|left|right|top-left|top-right|theme`, 대소문자·`-`/`_` 무시)을 운영자 값으로 — 모르는 값이면 null(무시).
     * @param {string | null} value - 주소의 `menu` 값.
     * @returns {SiteMenuSetting | null} 운영자 값 자리에 쓸 값.
     */
    static fromPreview(value: string | null): SiteMenuSetting | null {
        if (!value) return null;
        const upper = value.trim().toUpperCase().replace('-', '_');
        return UtilMenuPosition.SITE_SETTINGS.find((v) => v === upper) ?? null;
    }

    /**
     * 개발 미리보기 주소 값(`?top=logo|menu|theme`)을 운영자 최상위 항목 위치로 — 모르는 값이면 null.
     * @param {string | null} value - 주소의 `top` 값.
     * @returns {SiteTopItemsSetting | null} 운영자 값 자리에 쓸 값.
     */
    static topItemsFromPreview(value: string | null): SiteTopItemsSetting | null {
        if (!value) return null;
        const upper = value.trim().toUpperCase();
        return UtilMenuPosition.SITE_TOP_ITEMS.find((v) => v === upper) ?? null;
    }

    /**
     * 개발 미리보기 주소 값(`?width=content|full|theme`)을 운영자 상단 메뉴 폭으로 — 모르는 값이면 null.
     * @param {string | null} value - 주소의 `width` 값.
     * @returns {SiteMenuWidthSetting | null} 운영자 값 자리에 쓸 값.
     */
    static widthFromPreview(value: string | null): SiteMenuWidthSetting | null {
        if (!value) return null;
        const upper = value.trim().toUpperCase();
        return UtilMenuPosition.SITE_WIDTHS.find((v) => v === upper) ?? null;
    }

    /**
     * 개발 미리보기 주소 값(`?overflow=scrollbar|arrows|theme`)을 운영자 메뉴 넘침 값으로 — 모르는 값이면 null.
     * @param {string | null} value - 주소의 `overflow` 값.
     * @returns {SiteMenuOverflowSetting | null} 운영자 값 자리에 쓸 값.
     */
    static overflowFromPreview(value: string | null): SiteMenuOverflowSetting | null {
        if (!value) return null;
        const upper = value.trim().toUpperCase();
        return UtilMenuPosition.SITE_OVERFLOWS.find((v) => v === upper) ?? null;
    }

    /** 운영자 메뉴 모양의 기본 — 칸마다 테마 기본 따름 */
    static readonly SITE_MENU_STYLE_DEFAULT: Readonly<SiteMenuStyle> = Object.freeze(
        Object.fromEntries(MENU_STYLE_KEYS.map((key) => [key, 'THEME'])) as SiteMenuStyle,
    );

    /** 테마 선언이 비어 있을 때의 메뉴 모양(칸마다 첫 값 — 기본 테마 모양) */
    static readonly MENU_STYLE_FALLBACK: Readonly<MenuStyle> = Object.freeze(
        Object.fromEntries(MENU_STYLE_KEYS.map((key) => [key, MENU_STYLE_VALUES[key][0]])) as MenuStyle,
    );

    /**
     * 지금 쓸 메뉴 모양 — 칸마다 운영자 값(THEME 이 아니면) → 테마 기본값(`resolveTopItems`·`resolveWidth` 와 같은 규칙).
     * @param {SiteMenuStyle} site - 운영자 값.
     * @param {MenuStyle} themeDefault - 현재 테마의 기본값(`UtilTheme.menuStyleDefault`).
     * @returns {MenuStyle} 해석된 메뉴 모양.
     */
    static resolveStyle(site: SiteMenuStyle, themeDefault: MenuStyle): MenuStyle {
        const out = { ...themeDefault } as Record<MenuStyleKey, string>;
        for (const key of MENU_STYLE_KEYS) {
            if (site[key] !== 'THEME') out[key] = site[key];
        }
        return out as MenuStyle;
    }

    /**
     * 운영자 메뉴 모양 한 칸을 읽는다 — 없거나 모르는 값이면 `THEME`.
     * @param {K} key - 칸 이름.
     * @param {unknown} value - 후보 값(서버 응답·미리보기 주소, 대소문자 무시).
     * @returns 운영자 값. 모르는 값이면 null(미리보기는 무시, 서버 값은 `THEME`).
     */
    static readStyleValue<K extends MenuStyleKey>(key: K, value: unknown): SiteMenuStyle[K] | null {
        if (typeof value !== 'string') return null;
        const upper = value.trim().toUpperCase();
        if (upper === 'THEME') return 'THEME';
        return (MENU_STYLE_VALUES[key] as readonly string[]).includes(upper) ? (upper as SiteMenuStyle[K]) : null;
    }

    /**
     * 운영자 메뉴 모양을 서버 응답에서 읽는다 — 칸마다 없거나 모르는 값이면 `THEME`.
     * @param {Partial<Record<MenuStyleKey, unknown>>} res - 응답(공개 `ThemeResponse`).
     * @returns {SiteMenuStyle} 운영자 값.
     */
    static readSiteStyle(res: Partial<Record<MenuStyleKey, unknown>>): SiteMenuStyle {
        const out = { ...UtilMenuPosition.SITE_MENU_STYLE_DEFAULT } as Record<MenuStyleKey, string>;
        for (const key of MENU_STYLE_KEYS) out[key] = UtilMenuPosition.readStyleValue(key, res[key]) ?? 'THEME';
        return out as SiteMenuStyle;
    }

    /** 메뉴 모양 칸 목록(차례 고정) */
    static styleKeys(): readonly MenuStyleKey[] {
        return MENU_STYLE_KEYS;
    }

    /**
     * 사이드 메뉴의 쪽 — 사이드가 없는 위치(상단)면 null.
     * @param {MenuPosition} position - 메뉴 위치.
     * @returns {'LEFT' | 'RIGHT' | null} 사이드 쪽.
     */
    static sideOf(position: MenuPosition): 'LEFT' | 'RIGHT' | null {
        if (position === 'LEFT' || position === 'TOP_LEFT') return 'LEFT';
        if (position === 'RIGHT' || position === 'TOP_RIGHT') return 'RIGHT';
        return null;
    }

    /**
     * 상단 메뉴 막대가 있는 위치인가 — `TOP`·`TOP_LEFT`·`TOP_RIGHT`.
     * @param {MenuPosition} position - 메뉴 위치.
     * @returns {boolean} 상단 막대가 있으면 true.
     */
    static hasBar(position: MenuPosition): boolean {
        return position === 'TOP' || position === 'TOP_LEFT' || position === 'TOP_RIGHT';
    }

    /**
     * 상단 막대에 올릴 범위 — `TOP` 이면 모든 그룹(`barGroupIds` 무시), 사이드 단독이면 없음(무시), 섞인 배치면 고른 그룹 ID(비면 `UtilMenu.split` 이 첫 그룹으로 폴백).
     * @param {MenuPosition} position - 메뉴 위치.
     * @param {readonly string[]} barGroupIds - 운영자가 상단 띠에 고른 최상위 그룹 ID.
     * @returns {MenuBarScope} 상단 막대 범위.
     */
    static barScope(position: MenuPosition, barGroupIds: readonly string[]): MenuBarScope {
        if (position === 'TOP') return 'ALL';
        if (position === 'LEFT' || position === 'RIGHT') return 'NONE';
        return barGroupIds;
    }
}
