package com.community.site.dto;

import java.util.List;

/**
 * 테마 하나의 메뉴 설정(운영자 값) — 칸마다 {@code THEME} 은 그 테마의 기본값을 따른다. 허용 값·테마 기본값은 {@code SiteThemeMenus}.
 * 관리자 응답은 모양 7칸을 {@code menuStyle} 로 묶고, 공개 응답({@code GET /api/site/theme})은 평평하게 낸다.
 *
 * @param menuPosition TOP · LEFT · RIGHT · TOP_LEFT · TOP_RIGHT
 * @param topItems     LOGO · MENU
 * @param barGroupIds  섞인 배치에서 상단 띠에 둘 최상위 그룹 ID(공개 메뉴 main 의 폴더 ID) — 비면 첫 그룹
 * @param menuWidth    CONTENT · FULL
 * @param menuOverflow SCROLLBAR · ARROWS
 */
public record SiteThemeMenu(String menuPosition, String topItems, List<String> barGroupIds, String menuWidth, String menuOverflow, Style style) {

    public SiteThemeMenu {
        // 검사 전 요청 값에 null 이 섞일 수 있어 List.copyOf 대신 복사본을 감싼다(검사는 SiteThemeMenus.check)
        barGroupIds = barGroupIds == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(barGroupIds));
    }

    /**
     * 메뉴 모양 7칸 — user-web {@code MENU_STYLE_VALUES} 와 같은 이름·값.
     *
     * @param menuGroupStyle  TABS · INLINE
     * @param sideGroupStyle  COLUMN · PICK
     * @param menuSize        NORMAL · COMPACT
     * @param mobileMenu      PICKED · MENU
     * @param menuDescription ON · OFF
     * @param stickyMenu      ON · OFF
     * @param mobileHeader    BRAND · PLAIN
     */
    public record Style(String menuGroupStyle, String sideGroupStyle, String menuSize, String mobileMenu, String menuDescription, String stickyMenu,
                        String mobileHeader) {
    }
}
