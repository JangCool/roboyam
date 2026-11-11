package com.community.site.dto.request;

import com.community.site.dto.SiteThemeMenu;
import java.util.List;

/**
 * 테마 하나의 메뉴 설정 저장 요청({@code PUT /api/admin/site/themes/{id}/menu}) — admin-web {@code SiteThemeMenuFields}. 칸 전부를 통째로 받는다.
 * 값 검사는 서비스가 한다({@code SiteThemeMenus.check} — {@code fields.menuPosition} … {@code fields.menuStyle.<칸>}).
 *
 * @param barGroupIds 상단 띠 그룹 ID(문자열) — 비면 첫 그룹
 * @param menuStyle   메뉴 모양 7칸
 * @param menuBold    메뉴 글자 기본 굵게(V187) — 빠지면(null) 지금 값 유지
 */
public record SiteThemeMenuRequest(String menuPosition, String topItems, List<String> barGroupIds, String menuWidth, String menuOverflow,
                                   SiteThemeMenu.Style menuStyle, Boolean menuBold) {

    /** @param currentBold 지금 저장된 메뉴 글자 기본 굵게 — 요청이 {@code menuBold} 를 빼면 이 값 */
    public SiteThemeMenu toMenu(boolean currentBold) {
        return new SiteThemeMenu(menuPosition, topItems, barGroupIds, menuWidth, menuOverflow, menuStyle, menuBold == null ? currentBold : menuBold);
    }
}
