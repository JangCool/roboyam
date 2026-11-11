package com.community.site.service;

import com.community.site.dto.SiteThemeMenu;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 테마별 메뉴 설정의 허용 값·처음 값·테마 기본값 — 서버의 원천이다(site-theme-layout-contract 「필드·검증」).
 * 테마 기본값은 user-web {@code util-theme.ts} 의 {@code THEMES[*]}(menuPosition·topItems·menuWidth·menuOverflow·menuStyle + {@code MENU_STYLE_FALLBACK})와 같은 값이다 —
 * 관리자 화면의 「테마 기본(…)」 안내가 이 값을 받는다. 공개 사이트의 실제 해석은 user-web 이 자기 표로 한다(두 곳을 같이 고친다).
 */
public final class SiteThemeMenus {

    /** 칸마다 「테마 기본 따름」 */
    public static final String THEME = "THEME";

    public static final List<String> POSITIONS = List.of("TOP", "LEFT", "RIGHT", "TOP_LEFT", "TOP_RIGHT");
    public static final List<String> TOP_ITEMS = List.of("LOGO", "MENU");
    public static final List<String> WIDTHS = List.of("CONTENT", "FULL");
    public static final List<String> OVERFLOWS = List.of("SCROLLBAR", "ARROWS");

    /** 메뉴 모양 7칸의 허용 값(칸 이름 → 값) — user-web {@code MENU_STYLE_VALUES} */
    public static final Map<String, List<String>> STYLE_VALUES = styleValues();

    /** 상단 띠 그룹 ID 개수 한도 */
    public static final int BAR_GROUPS_MAX = 20;

    /** 메뉴 글자 기본 굵게(V187)를 처음부터 켜 둔 테마 — 다음 테마만(마이그레이션 V187 의 UPDATE 와 같은 규칙) */
    public static final Set<String> MENU_BOLD_THEMES = Set.of("daum", "roboyam"); // 같은 막대(DaumMenuBar) — 예전부터 굵게 보였다(사용자 2026-11-11)

    private static final SiteThemeMenu.Style INITIAL_STYLE = new SiteThemeMenu.Style(THEME, THEME, THEME, THEME, THEME, THEME, THEME);

    private static final SiteThemeMenu.Style BASE_STYLE = new SiteThemeMenu.Style("TABS", "COLUMN", "NORMAL", "PICKED", "OFF", "OFF", "BRAND");

    /** 테마 기본값(THEME 없음, 상단 띠 그룹은 늘 빈 목록) */
    private static final Map<String, SiteThemeMenu> DEFAULTS = Map.of(
            "default", new SiteThemeMenu("LEFT", "MENU", List.of(), "CONTENT", "SCROLLBAR", BASE_STYLE, false),
            "daum", new SiteThemeMenu("TOP", "MENU", List.of(), "CONTENT", "SCROLLBAR", BASE_STYLE, true),
            "naver", new SiteThemeMenu("TOP", "LOGO", List.of(), "CONTENT", "SCROLLBAR", BASE_STYLE, false),
            "roboyam", new SiteThemeMenu("TOP_RIGHT", "MENU", List.of(), "FULL", "SCROLLBAR",
                    new SiteThemeMenu.Style("INLINE", "PICK", "COMPACT", "MENU", "ON", "ON", "PLAIN"), false));

    private SiteThemeMenus() {
    }

    /** 저장한 적 없는 테마의 메뉴 설정 — 칸마다 테마 기본 따름 · 빈 상단 띠 그룹 · 메뉴 글자 기본 굵게는 {@link #MENU_BOLD_THEMES} 만 켬 */
    public static SiteThemeMenu initialOf(String theme) {
        return new SiteThemeMenu(THEME, THEME, List.of(), THEME, THEME, INITIAL_STYLE, MENU_BOLD_THEMES.contains(theme));
    }

    /** 그 테마의 기본값 — 모르는 테마는 기본 테마의 것 */
    public static SiteThemeMenu defaultsOf(String theme) {
        return DEFAULTS.getOrDefault(theme, DEFAULTS.get(SiteThemes.DEFAULT));
    }

    /** 고를 수 있는 테마마다의 기본값(테마 목록 차례) */
    public static Map<String, SiteThemeMenu> allDefaults() {
        Map<String, SiteThemeMenu> out = new LinkedHashMap<>();
        SiteThemes.IDS.forEach(id -> out.put(id, defaultsOf(id)));
        return out;
    }

    /**
     * 저장 요청을 검사해 정리한 값으로 — 칸마다 {@code THEME} 또는 허용 값, 상단 띠 그룹은 숫자 ID 글자(중복 제거, {@value #BAR_GROUPS_MAX}개까지).
     * 빠진 칸도 칸 오류다(메뉴 설정 전체를 통째로 받는다) — 켬·끔인 {@code menuBold} 는 부르는 쪽이 빠진 값을 지금 값으로 채워 넘긴다.
     *
     * @throws ValidationException {@code fields.menuPosition|topItems|barGroupIds|menuWidth|menuOverflow|menuStyle.<칸>}
     */
    public static SiteThemeMenu check(SiteThemeMenu input) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        SiteThemeMenu in = input == null ? new SiteThemeMenu(null, null, null, null, null, null, false) : input;
        choice(fields, "menuPosition", in.menuPosition(), POSITIONS);
        choice(fields, "topItems", in.topItems(), TOP_ITEMS);
        List<String> bar = barGroupIds(in.barGroupIds(), fields);
        choice(fields, "menuWidth", in.menuWidth(), WIDTHS);
        choice(fields, "menuOverflow", in.menuOverflow(), OVERFLOWS);
        SiteThemeMenu.Style style = in.style() == null ? new SiteThemeMenu.Style(null, null, null, null, null, null, null) : in.style();
        // 빠진 칸(null)도 칸 오류로 돌려주려고 null 을 받는 목록을 쓴다(List.of 는 null 에 NPE)
        List<String> styleValues = java.util.Arrays.asList(style.menuGroupStyle(), style.sideGroupStyle(), style.menuSize(), style.mobileMenu(), style.menuDescription(),
                style.stickyMenu(), style.mobileHeader());
        int i = 0;
        for (Map.Entry<String, List<String>> e : STYLE_VALUES.entrySet()) {
            choice(fields, "menuStyle." + e.getKey(), styleValues.get(i++), e.getValue());
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return new SiteThemeMenu(in.menuPosition(), in.topItems(), bar, in.menuWidth(), in.menuOverflow(), style, in.menuBold());
    }

    private static void choice(Map<String, MessageRef> fields, String field, String value, List<String> allowed) {
        if (value == null || (!THEME.equals(value) && !allowed.contains(value))) {
            fields.put(field, new MessageRef("siteThemeMenu.choice"));
        }
    }

    private static List<String> barGroupIds(List<String> raw, Map<String, MessageRef> fields) {
        if (raw == null) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String id : raw) {
            if (id == null || !id.matches("[0-9]{1,19}")) {
                fields.put("barGroupIds", new MessageRef("siteThemeMenu.barGroupIds.invalid"));
                return List.of();
            }
            out.add(id);
        }
        if (out.size() > BAR_GROUPS_MAX) {
            fields.put("barGroupIds", new MessageRef("siteThemeMenu.barGroupIds.max", String.valueOf(BAR_GROUPS_MAX)));
            return List.of();
        }
        return new ArrayList<>(out);
    }

    private static Map<String, List<String>> styleValues() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("menuGroupStyle", List.of("TABS", "INLINE"));
        values.put("sideGroupStyle", List.of("COLUMN", "PICK"));
        values.put("menuSize", List.of("NORMAL", "COMPACT"));
        values.put("mobileMenu", List.of("PICKED", "MENU"));
        values.put("menuDescription", List.of("ON", "OFF"));
        values.put("stickyMenu", List.of("ON", "OFF"));
        values.put("mobileHeader", List.of("BRAND", "PLAIN"));
        return java.util.Collections.unmodifiableMap(values);
    }
}
