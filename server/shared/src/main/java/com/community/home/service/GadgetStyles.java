package com.community.home.service;

import java.util.List;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 가젯 공통 꾸미기(⚙ 설정 창 「모양」·「노출」, gadget-page-contract 「가젯 설정」 — 모양·노출) — 칸·값·글자 한도. admin-web {@code UtilGadgetStyle} · 공개 읽기 user-web
 * {@code readStyle} 과 같은 값. 검사는 관리자 서버 {@code GadgetPatches}.
 * {@code icon} {@code ^pi-[a-z0-9-]{2,30}$}·null · {@code headerStyle} · {@code titleSize} · {@code titleColor}·{@code cardColor} 프리셋 12색 이름·{@code #rrggbb}·null ·
 * {@code subtitle} 0~40 · {@code cardLook} · {@code mobileFold} · {@code empty{keep, message, text 0~40}}.
 */
public final class GadgetStyles {

    public static final List<String> HEADER_STYLES = List.of("BAR", "UNDERLINE", "FILLED", "PLAIN", "HIDDEN");
    public static final List<String> TITLE_SIZES = List.of("S", "M", "L");
    public static final List<String> CARD_LOOKS = List.of("DEFAULT", "FLAT", "OUTLINE", "TINT");
    public static final List<String> MOBILE_FOLDS = List.of("NONE", "OPEN", "CLOSED");
    /** 색 프리셋 12색 — user-web {@code UtilColor.PRESET_NAMES} 와 같은 이름 */
    public static final List<String> COLOR_PRESETS = List.of("BLUE", "TEAL", "AMBER", "PURPLE", "GREEN", "ORANGE", "SLATE", "RED", "PINK", "INDIGO", "SKY", "BROWN");
    public static final int SUBTITLE_MAX = 40;
    public static final int EMPTY_TEXT_MAX = 40;
    public static final Pattern ICON = Pattern.compile("^pi-[a-z0-9-]{2,30}$");
    public static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private GadgetStyles() {
    }

    /** 색 값 — 프리셋 이름은 그대로, {@code #rrggbb} 는 소문자로, 그 밖은 null */
    public static String color(String value) {
        if (value == null) {
            return null;
        }
        if (COLOR_PRESETS.contains(value)) {
            return value;
        }
        return HEX.matcher(value).matches() ? value.toLowerCase(java.util.Locale.ROOT) : null;
    }

    /**
     * 관리자 편집기가 보는 꾸미기 — 칸 전부. 테마 기본 꾸미기({@link HomeGadgetKinds#baseStyle}) 위에 저장 값 중 쓸 수 있는 칸을 덮는다(모르는 값은 테마 기본 값).
     * 게시판 본문(꾸미기 없음)은 null.
     */
    public static ObjectNode view(String theme, String type, JsonNode stored) {
        if (!HomeGadgetKinds.isKnown(type)) {
            return null;
        }
        ObjectNode out = HomeGadgetKinds.baseStyle(theme);
        if (stored == null || !stored.isObject()) {
            return out;
        }
        JsonNode icon = stored.get("icon");
        if (icon != null && (icon.isNull() || icon.isString() && ICON.matcher(icon.asString()).matches())) {
            if (icon.isNull()) {
                out.putNull("icon");
            } else {
                out.put("icon", icon.asString());
            }
        }
        pick(out, stored, "headerStyle", HEADER_STYLES);
        pick(out, stored, "titleSize", TITLE_SIZES);
        pick(out, stored, "cardLook", CARD_LOOKS);
        pick(out, stored, "mobileFold", MOBILE_FOLDS);
        pickColor(out, stored, "titleColor");
        pickColor(out, stored, "cardColor");
        String subtitle = HomeLayoutJson.text(stored, "subtitle", null);
        if (subtitle != null && subtitle.codePointCount(0, subtitle.length()) <= SUBTITLE_MAX) {
            out.put("subtitle", subtitle);
        }
        JsonNode empty = stored.get("empty");
        if (empty != null && empty.isObject()) {
            ObjectNode e = (ObjectNode) out.get("empty");
            if (empty.path("keep").isBoolean()) {
                e.put("keep", empty.get("keep").asBoolean());
            }
            if (empty.path("message").isBoolean()) {
                e.put("message", empty.get("message").asBoolean());
            }
            String text = HomeLayoutJson.text(empty, "text", null);
            if (text != null && text.codePointCount(0, text.length()) <= EMPTY_TEXT_MAX) {
                e.put("text", text);
            }
        }
        return out;
    }

    private static void pick(ObjectNode out, JsonNode stored, String field, List<String> values) {
        String v = HomeLayoutJson.text(stored, field, null);
        if (v != null && values.contains(v)) {
            out.put(field, v);
        }
    }

    private static void pickColor(ObjectNode out, JsonNode stored, String field) {
        JsonNode v = stored.get(field);
        if (v == null) {
            return;
        }
        String color = v.isString() ? color(v.asString()) : null;
        if (color != null) {
            out.put(field, color);
        } else if (v.isNull()) {
            out.putNull(field);
        }
    }
}
