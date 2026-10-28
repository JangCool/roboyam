package com.community.site.entity;

import com.community.site.dto.SiteThemeMenu;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * 테마 하나의 메뉴 설정({@code site_theme_menus}, V105) — 테마마다 한 행. 행이 없으면 처음 값(칸마다 테마 기본 따름)이다.
 * 값 검사는 저장하는 쪽({@code SiteThemeMenus.check})이 끝낸다. 테마 id 는 {@code site_settings.theme} 과 같이 CHECK 없이 둔다(테마가 늘어도 마이그레이션이 없게).
 */
@Entity
@Table(name = "site_theme_menus")
public class SiteThemeMenuEntity {

    @Id
    private String theme;

    private String menuPosition;
    private String topItems;
    /** 상단 띠 그룹 ID 를 쉼표로 이은 글자(빈 글자 = 없음) */
    private String barGroupIds;
    private String menuWidth;
    private String menuOverflow;
    private String menuGroupStyle;
    private String sideGroupStyle;
    private String menuSize;
    private String mobileMenu;
    private String menuDescription;
    private String stickyMenu;
    private String mobileHeader;
    private Long updatedBy;
    private Instant updatedAt;

    protected SiteThemeMenuEntity() {
    }

    public static SiteThemeMenuEntity create(String theme, SiteThemeMenu menu, long operatorId, Instant now) {
        SiteThemeMenuEntity row = new SiteThemeMenuEntity();
        row.theme = theme;
        row.apply(menu, operatorId, now);
        return row;
    }

    /** 값을 바꾼다 — 같은 값이면 아무것도 바꾸지 않고 false */
    public boolean change(SiteThemeMenu menu, long operatorId, Instant now) {
        if (toMenu().equals(menu)) {
            return false;
        }
        apply(menu, operatorId, now);
        return true;
    }

    private void apply(SiteThemeMenu menu, long operatorId, Instant now) {
        menuPosition = menu.menuPosition();
        topItems = menu.topItems();
        barGroupIds = String.join(",", menu.barGroupIds());
        menuWidth = menu.menuWidth();
        menuOverflow = menu.menuOverflow();
        menuGroupStyle = menu.style().menuGroupStyle();
        sideGroupStyle = menu.style().sideGroupStyle();
        menuSize = menu.style().menuSize();
        mobileMenu = menu.style().mobileMenu();
        menuDescription = menu.style().menuDescription();
        stickyMenu = menu.style().stickyMenu();
        mobileHeader = menu.style().mobileHeader();
        updatedBy = operatorId;
        updatedAt = now;
    }

    public SiteThemeMenu toMenu() {
        List<String> bar = barGroupIds == null || barGroupIds.isEmpty() ? List.of() : Arrays.asList(barGroupIds.split(","));
        return new SiteThemeMenu(menuPosition, topItems, bar, menuWidth, menuOverflow,
                new SiteThemeMenu.Style(menuGroupStyle, sideGroupStyle, menuSize, mobileMenu, menuDescription, stickyMenu, mobileHeader));
    }

    public String getTheme() {
        return theme;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
