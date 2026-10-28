/**
 * `GET /api/site/theme` 응답 — 운영자가 사이트 설정에서 고른 테마 id. 모르는 id 는 화면이 기본 테마로 그린다.
 * 아래 칸은 **지금 테마의** 운영자 메뉴 설정이다(서버 `site_theme_menus`, 2026-10-29 — 칸마다 `THEME` 또는 값. 없거나 모르는 값이면 기본값, `UtilMenuPosition.read*`):
 *  - `menuPosition` 데스크톱 메뉴 위치(`THEME`·`TOP`·`LEFT`·`RIGHT`·`TOP_LEFT`·`TOP_RIGHT`, 기본 `THEME`)
 *  - `topItems` 최상위 항목 위치(`THEME`·`LOGO`·`MENU`, 기본 `THEME`)
 *  - `barGroupIds` 섞인 배치에서 상단 띠에 둘 최상위 그룹 ID(기본 빈 목록 = 첫 그룹)
 *  - `menuWidth` 상단 메뉴 폭(`THEME`·`CONTENT`·`FULL`, 기본 `THEME`) — 상단 메뉴가 있는 배치에서만 쓰인다
 *  - `menuOverflow` 가로 메뉴 줄이 넘칠 때의 표시(`THEME`·`SCROLLBAR`·`ARROWS`, 기본 `THEME`)
 *  - 메뉴 모양 7칸(사용자 결정 2026-10-28, 칸마다 기본 `THEME` — 뜻은 `UtilMenuPosition` 의 `MENU_STYLE_VALUES`, `UtilMenuPosition.readSiteStyle`):
 *    `menuGroupStyle`(`TABS`·`INLINE`) · `sideGroupStyle`(`COLUMN`·`PICK`) · `menuSize`(`NORMAL`·`COMPACT`) · `mobileMenu`(`PICKED`·`MENU`) ·
 *    `menuDescription`(`ON`·`OFF`) · `stickyMenu`(`ON`·`OFF`) · `mobileHeader`(`BRAND`·`PLAIN`)
 */
export interface ThemeResponse {
    theme: string;
    menuPosition?: string;
    topItems?: string;
    barGroupIds?: string[];
    menuWidth?: string;
    menuOverflow?: string;
    menuGroupStyle?: string;
    sideGroupStyle?: string;
    menuSize?: string;
    mobileMenu?: string;
    menuDescription?: string;
    stickyMenu?: string;
    mobileHeader?: string;
}
