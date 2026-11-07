package com.community.menu.target;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 앱 화면 레지스트리({@code BUILTIN} 의 {@code targetKey}) — 공개 사이트(user-web)의 코드 화면. admin-web {@code UtilMenu.BUILTINS} 와 <b>같은 표</b>다
 * (키 · 카테고리 · 주소 · matchPrefix · 로그인 필요 · 판매 스위치 필요). 기본 이름은 메시지 번들 {@code menu.builtin.<키>}(ko·en).
 * 화면이 새로 생기면 admin-web 표·user-web 화면·번들과 함께 한 줄 더한다.
 */
public enum MenuBuiltin {

    HOME("GENERAL", "/", false, false, false),
    BOARDS_HUB("GENERAL", "/boards", false, false, false),
    POPULAR("GENERAL", "/popular", true, false, false),
    LATEST("GENERAL", "/latest", true, false, false),
    SEARCH("GENERAL", "/search", true, false, false),
    GROUP_BUYS("GROUP_BUY", "/group-buys", true, false, true),
    GROUP_BUY_ORDERS("GROUP_BUY", "/me/orders", true, true, true),
    GROUPS_ALL("GROUPS", "/groups", true, false, false),
    GROUPS_MINE("GROUPS", "/groups/me", false, true, false),
    GROUPS_NEW("GROUPS", "/groups/new", false, true, false),
    MY_HOME("MYPAGE", "/me", false, true, false),
    MY_GRADE("MYPAGE", "/me/grade", false, true, false),
    MY_POINTS("MYPAGE", "/me/points", false, true, false),
    /** 포인트 상점(커머스 1-2 — 주소 /me/shop → /point-shop, 상세 /point-shop/{id} 도 이 메뉴로 본다) */
    MY_SHOP("MYPAGE", "/point-shop", true, true, false),
    MY_SCHEDULED("MYPAGE", "/scheduled", false, true, false),
    MESSAGES("MYPAGE", "/messages", true, true, false),
    NOTIFICATIONS("MYPAGE", "/notifications", false, true, false),
    NOTIFICATION_SETTINGS("MYPAGE", "/settings/notifications", false, true, false),
    SETTINGS_PROFILE("MYPAGE", "/settings/profile", false, true, false),
    SETTINGS_ACCOUNT("MYPAGE", "/settings/account", true, true, false),
    SETTINGS_VIEW("MYPAGE", "/settings/view", false, true, false),
    SETTINGS_DISPLAY("MYPAGE", "/settings/display", false, true, false),
    SETTINGS_MESSAGES("MYPAGE", "/settings/messages", false, true, false),
    SETTINGS_NICKNAME_IMAGE("MYPAGE", "/settings/nickname-image", false, true, false),
    SETTINGS_BLOCKS("MYPAGE", "/settings/blocks", false, true, false),
    SETTINGS_MEMOS("MYPAGE", "/settings/memos", false, true, false);

    private static final Map<String, MenuBuiltin> BY_KEY = Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(MenuBuiltin::name, Function.identity()));

    private final String category;
    private final String path;
    private final boolean matchPrefix;
    private final boolean loginRequired;
    private final boolean salesGated;

    MenuBuiltin(String category, String path, boolean matchPrefix, boolean loginRequired, boolean salesGated) {
        this.category = category;
        this.path = path;
        this.matchPrefix = matchPrefix;
        this.loginRequired = loginRequired;
        this.salesGated = salesGated;
    }

    public static Optional<MenuBuiltin> of(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(BY_KEY.get(key));
    }

    public String category() {
        return category;
    }

    public String path() {
        return path;
    }

    public boolean matchPrefix() {
        return matchPrefix;
    }

    /** 로그인한 사람에게만 보이게 할 수 있다(보이는 대상이 MEMBER·GRADE_MIN·STAFF 여야 한다) */
    public boolean loginRequired() {
        return loginRequired;
    }

    /** 공동구매 판매 스위치가 꺼지면 숨는다 */
    public boolean salesGated() {
        return salesGated;
    }

    /**
     * 사이트 안 주소가 판매 스위치가 필요한 앱 화면(공동구매 목록·주문 내역)을 가리키나 — 주소 직접 입력({@code PATH}) 항목도 같은 판정을 받게 한다(2026-11-07).
     * 물음표·샵 뒤는 보지 않고, 그 화면의 주소와 같거나(아래 주소를 밝히는 화면이면) 그 아래 주소면 참.
     */
    public static boolean salesGatedPath(String path) {
        if (path == null) {
            return false;
        }
        String p = path.strip();
        int cut = p.indexOf('?') >= 0 ? p.indexOf('?') : p.indexOf('#');
        if (cut >= 0) {
            p = p.substring(0, cut);
        }
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        for (MenuBuiltin b : values()) {
            if (b.salesGated && (p.equals(b.path) || (b.matchPrefix && p.startsWith(b.path + "/")))) {
                return true;
            }
        }
        return false;
    }

    /** 기본 이름의 메시지 키 */
    public String labelKey() {
        return "menu.builtin." + name();
    }
}
