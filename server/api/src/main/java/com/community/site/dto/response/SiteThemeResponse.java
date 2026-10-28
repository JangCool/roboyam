package com.community.site.dto.response;

import java.util.List;

/**
 * {@code GET /api/site/theme}(비로그인) — 운영자가 고른 공개 사이트 테마와 <b>그 테마의</b> 메뉴 설정(평평한 칸). user-web {@code ThemeResponse} 와 같은 모양이고
 * {@code theme} 은 {@code /api/site/info} 의 {@code theme} 과 같은 값이다. 메뉴 칸은 칸마다 {@code THEME}(테마 기본 따름) 또는 운영자 값이다 — 테마 기본값의 해석은 화면이 한다.
 *
 * @param theme       테마 id — 설정 행이 없거나 목록에 없는 값이면 {@code default}
 * @param barGroupIds 섞인 배치에서 상단 띠에 둘 최상위 그룹 ID — 비면 첫 그룹
 */
public record SiteThemeResponse(String theme, String menuPosition, String topItems, List<String> barGroupIds, String menuWidth, String menuOverflow,
                                String menuGroupStyle, String sideGroupStyle, String menuSize, String mobileMenu, String menuDescription, String stickyMenu,
                                String mobileHeader) {
}
