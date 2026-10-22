package com.community.menu.service;

import com.community.zconfiguration.message.MessageRef;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 메뉴 칸 검사의 순수 함수 — 이름·영문명·사이트 안 주소·외부 링크 주소. admin-web {@code UtilMenu.labelProblem}·{@code nameEnProblem}·{@code pathProblem}·{@code hrefProblem} 과
 * 같은 규칙이다(관리자 메뉴의 외부 링크도 같은 주소 규칙을 쓴다). 문제가 없으면 null.
 */
public final class MenuRules {

    private static final Pattern PATH_CHARS = Pattern.compile("[\\s\"'<>\\\\`]");
    private static final Pattern SCHEME = Pattern.compile("^[a-z][a-z0-9+.-]*:", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHITESPACE = Pattern.compile("\\s");
    private static final Pattern HREF_CHARS = Pattern.compile("[<>\"'\\\\`]");
    private static final Pattern AUTHORITY = Pattern.compile("^[a-z0-9.-]+(:\\d{1,5})?$", Pattern.CASE_INSENSITIVE);

    private MenuRules() {
    }

    /** 앞뒤 공백을 지운 글자, 비면 null */
    public static String stripToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /** 이름 — 비면 {@code required} 일 때만 문제, {@code <}·{@code >}·줄바꿈·제어 문자 금지, 글자 수(코드 포인트) ≤ {@code max} */
    public static MessageRef labelProblem(String value, boolean required, int max) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            return required ? new MessageRef("menu.label.required") : null;
        }
        if (hasForbiddenChars(value)) {
            return new MessageRef("menu.label.chars");
        }
        if (text.codePointCount(0, text.length()) > max) {
            return new MessageRef("menu.label.length", String.valueOf(max));
        }
        return null;
    }

    /** 영문명 — 선택, 같은 금지 글자, ≤ 40자 */
    public static MessageRef nameEnProblem(String value) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            return null;
        }
        if (hasForbiddenChars(value)) {
            return new MessageRef("menu.label.chars");
        }
        if (text.length() > MenuLimits.NAME_EN_MAX) {
            return new MessageRef("menu.nameEn.length", String.valueOf(MenuLimits.NAME_EN_MAX));
        }
        return null;
    }

    /** 사이트 안 주소 — {@code /} 로 시작, 500자 이하, {@code //}·스킴·{@code /api}·{@code /admin} 구간·공백·따옴표·꺾쇠·역슬래시·{@code .}/{@code ..} 구간 거절 */
    public static MessageRef pathProblem(String value) {
        String path = value == null ? "" : value.strip();
        if (path.isEmpty()) {
            return new MessageRef("menu.path.required");
        }
        if (path.length() > MenuLimits.PATH_MAX) {
            return new MessageRef("menu.path.length", String.valueOf(MenuLimits.PATH_MAX));
        }
        return isValidPath(path) ? null : new MessageRef("menu.path.invalid");
    }

    /** 외부 링크 — {@code https://} 절대 주소 또는 {@code /…}(사이트 안 주소 규칙), 2000자 이하, {@code http://}·다른 스킴·{@code //}·공백·계정정보 거절 */
    public static MessageRef hrefProblem(String value) {
        String href = value == null ? "" : value.strip();
        if (href.isEmpty() || "https://".equals(href)) {
            return new MessageRef("menu.href.required");
        }
        if (href.length() > MenuLimits.HREF_MAX) {
            return new MessageRef("menu.href.length", String.valueOf(MenuLimits.HREF_MAX));
        }
        if (WHITESPACE.matcher(href).find() || href.startsWith("//")) {
            return new MessageRef("menu.href.invalid");
        }
        if (href.startsWith("/")) {
            return href.length() <= MenuLimits.PATH_MAX && isValidPath(href) ? null : new MessageRef("menu.href.invalid");
        }
        if (href.regionMatches(true, 0, "http://", 0, 7)) {
            return new MessageRef("menu.href.http");
        }
        if (!isHttps(href) || HREF_CHARS.matcher(href).find()) {
            return new MessageRef("menu.href.invalid");
        }
        String authority = href.substring("https://".length()).split("[/?#]", -1)[0];
        if (authority.contains("@") || authority.isEmpty() || !AUTHORITY.matcher(authority).matches()) {
            return new MessageRef("menu.href.invalid");
        }
        return null;
    }

    /** 다른 도메인으로 가는 주소인가({@code https://} 로 시작) */
    public static boolean isHttps(String href) {
        return href != null && href.regionMatches(true, 0, "https://", 0, 8);
    }

    private static boolean isValidPath(String path) {
        if (PATH_CHARS.matcher(path).find() || !path.startsWith("/") || path.contains("//")) {
            return false;
        }
        String[] raw = path.split("[?#]", -1)[0].split("/");
        String first = null;
        for (String segment : raw) {
            if (segment.isEmpty()) {
                continue;
            }
            if (".".equals(segment) || "..".equals(segment)) {
                return false;
            }
            if (first == null) {
                first = segment;
            }
        }
        if (first != null) {
            String lower = first.toLowerCase(Locale.ROOT);
            if ("api".equals(lower) || "admin".equals(lower) || SCHEME.matcher(first).find()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasForbiddenChars(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '<' || c == '>' || c < 0x20 || c == 0x7f) {
                return true;
            }
        }
        return false;
    }
}
