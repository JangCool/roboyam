package com.community.site.dto.response;

import com.community.site.dto.SiteThemeMenu;
import java.util.List;
import java.util.Map;

/**
 * 공개 사이트 테마 + 한 테마의 메뉴 설정 — admin-web {@code SiteThemeResponse}. 테마 이름(화면 문구)은 화면이 id 로 i18n 키를 찾는다.
 * 메뉴 설정 칸은 {@code menuTheme} 테마의 것이다(조회 {@code forTheme}, 없으면 지금 테마). 칸마다 {@code THEME} 은 그 테마의 기본값을 따른다.
 *
 * @param theme                     지금 테마 id(저장된 값이 목록에서 빠졌으면 기본 테마 {@code default})
 * @param themes                    고를 수 있는 테마 id(보일 순서)
 * @param menuTheme                 아래 메뉴 설정 칸이 어느 테마의 것인가
 * @param menuStyle                 메뉴 모양 7칸
 * @param themeMenuDefaults         테마 id → 그 테마의 메뉴 위치 기본값(「테마 기본(…)」 안내용, 아래 넷도 같다)
 * @param themeMenuStyleDefaults    테마 id → 그 테마의 메뉴 모양 기본값
 */
public record SiteThemeResponse(String theme, List<String> themes, String menuTheme, String menuPosition, String topItems, List<String> barGroupIds,
                                String menuWidth, String menuOverflow, SiteThemeMenu.Style menuStyle, Map<String, String> themeMenuDefaults,
                                Map<String, String> themeTopItemsDefaults, Map<String, String> themeMenuWidthDefaults,
                                Map<String, String> themeMenuOverflowDefaults, Map<String, SiteThemeMenu.Style> themeMenuStyleDefaults) {
}
