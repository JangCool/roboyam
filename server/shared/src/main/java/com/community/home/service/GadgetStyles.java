package com.community.home.service;

import java.util.List;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 가젯 공통 꾸미기(⚙ 설정 창 「모양」·「노출」, gadget-page-contract 「가젯 설정」 — 모양·노출) — 칸·값·글자 한도. admin-web {@code UtilGadgetStyle} · 공개 읽기 user-web
 * {@code readStyle} 과 같은 값. 검사는 관리자 서버 {@code GadgetPatches}.
 * {@code icon} {@code ^pi-[a-z0-9-]{2,30}$}·null · {@code headerStyle} · {@code titleSize} · {@code titleColor}·{@code cardColor} 프리셋 12색 이름·{@code #rrggbb}·null ·
 * {@code subtitle} 0~40 · {@code cardLook} · {@code mobileFold} · {@code empty{keep, message, text 0~40}}.
 *
 * <p>제목 아이콘(gadget-page-contract 「가젯 제목 아이콘」, 사용자 시안 확정 2026-11-10 — 모든 종류 공통):
 * {@code iconShow} 보이기(테마 기본 — 모든 테마 켬, 2026-11-10 아이로빗도 켬) · {@code iconSource} PRIME·TABLER·UPLOAD(기본 PRIME) ·
 * {@code icon}(PRIME 이름 {@code ^pi-[a-z0-9-]{2,60}$}) · {@code iconTabler{name, paths[{d, fill?, stroke?, opacity?}]}}(Tabler Icons outline 한 개 — 이름과 그림 조각을 함께 둔다,
 * 공개 화면은 그 조각만 그린다) · {@code iconUpload{fileId, url, svg}}(올린 파일 — 주소·SVG 여부는 저장 때 서버가 채운다) ·
 * {@code iconColor} null(글자색 그대로)·프리셋·{@code #rrggbb} · {@code iconBg} NONE·SOFT(옅은 네모 — 테마 기본: 아이로빗 SOFT, 그 밖 NONE). 칸이 없는 옛 가젯은 테마 기본으로 읽는다.
 */
public final class GadgetStyles {

    public static final List<String> HEADER_STYLES = List.of("BAR", "UNDERLINE", "FILLED", "PLAIN", "HIDDEN");
    public static final List<String> TITLE_SIZES = List.of("S", "M", "L");
    public static final List<String> CARD_LOOKS = List.of("DEFAULT", "FLAT", "OUTLINE", "TINT");
    public static final List<String> MOBILE_FOLDS = List.of("NONE", "OPEN", "CLOSED");
    /** 색 프리셋 12색 — user-web {@code UtilColor.PRESET_NAMES} 와 같은 이름 */
    public static final List<String> COLOR_PRESETS = List.of("BLUE", "TEAL", "AMBER", "PURPLE", "GREEN", "ORANGE", "SLATE", "RED", "PINK", "INDIGO", "SKY", "BROWN", "EMERALD");
    public static final int SUBTITLE_MAX = 40;
    public static final int EMPTY_TEXT_MAX = 40;
    /** PrimeIcons 이름 — 가장 긴 것이 49자(pi-arrow-up-right-and-arrow-down-left-from-center)라 60자까지 */
    public static final Pattern ICON = Pattern.compile("^pi-[a-z0-9-]{2,60}$");
    public static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");
    public static final String ICON_PRIME = "PRIME";
    public static final String ICON_TABLER = "TABLER";
    public static final String ICON_UPLOAD = "UPLOAD";
    public static final List<String> ICON_SOURCES = List.of(ICON_PRIME, ICON_TABLER, ICON_UPLOAD);
    public static final List<String> ICON_BGS = List.of("NONE", "SOFT");
    /** Tabler 아이콘 이름 */
    public static final Pattern TABLER_NAME = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    public static final int TABLER_NAME_MAX = 60;
    /** Tabler 그림 조각 — 한 아이콘의 path 수 · path 한 개의 {@code d} 글자 수 · 전체 글자 수 한도(3.49.0 outline 은 최대 21개 · 943자 · 1,340자) */
    public static final int TABLER_PATHS_MAX = 40;
    public static final int TABLER_D_MAX = 3000;
    public static final int TABLER_TOTAL_MAX = 12000;
    /** path {@code d} — SVG 경로 명령·숫자만 */
    public static final Pattern PATH_D = Pattern.compile("^[MmLlHhVvCcSsQqTtAaZz0-9.,\\s+\\-eE]+$");
    /** path {@code fill}·{@code stroke} 값 */
    public static final List<String> PATH_PAINTS = List.of("currentColor", "none");
    /** path {@code opacity} — 0~1 */
    public static final Pattern OPACITY = Pattern.compile("^(?:0|1|0?\\.\\d{1,3}|1\\.0{1,3})$");
    /** 올린 파일 번호 */
    public static final Pattern FILE_ID = Pattern.compile("^[0-9]{1,19}$");

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
     * 관리자 편집기가 보는 꾸미기 — 칸 전부. 테마 기본 꾸미기({@link GadgetKinds#baseStyle}) 위에 저장 값 중 쓸 수 있는 칸을 덮는다(모르는 값은 테마 기본 값).
     * 게시판 본문(꾸미기 없음)은 null. 매거진 4종({@link GadgetKinds#isTips})은 다른 종류처럼 칸 전부.
     */
    public static ObjectNode view(String theme, String type, JsonNode stored) {
        if (!GadgetKinds.isKnown(type) && !GadgetKinds.isTips(type)) {
            return null;
        }
        ObjectNode out = GadgetKinds.kindStyle(GadgetKinds.baseStyle(theme), type);
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
        // 제목 아이콘 — 모양이 맞는 저장 값만(아니면 테마 기본)
        if (stored.path("iconShow").isBoolean()) {
            out.put("iconShow", stored.get("iconShow").asBoolean());
        }
        pick(out, stored, "iconSource", ICON_SOURCES);
        pick(out, stored, "iconBg", ICON_BGS);
        pickColor(out, stored, "iconColor");
        ObjectNode tabler = tabler(stored.get("iconTabler"));
        if (tabler != null) {
            out.set("iconTabler", tabler);
        }
        ObjectNode upload = storedUpload(stored.get("iconUpload"));
        if (upload != null) {
            out.set("iconUpload", upload);
        }
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

    /**
     * Tabler 아이콘 한 개 읽기 — {@code {name, paths[{d, fill?, stroke?, opacity?}]}} 모양이 맞으면 아는 칸만 담은 사본, 아니면 null.
     * 그림 조각은 path 하나뿐이고 값은 경로 명령·숫자 · {@code currentColor}/{@code none} · 0~1 만(공개 화면이 글자 그대로 그리지 않고 요소를 만들어 그린다).
     */
    public static ObjectNode tabler(JsonNode raw) {
        if (raw == null || !raw.isObject()) {
            return null;
        }
        String name = HomeLayoutJson.text(raw, "name", null);
        JsonNode paths = raw.get("paths");
        if (name == null || name.length() > TABLER_NAME_MAX || !TABLER_NAME.matcher(name).matches() || paths == null || !paths.isArray() || paths.isEmpty()
                || paths.size() > TABLER_PATHS_MAX) {
            return null;
        }
        ObjectNode out = HomeLayoutJson.object().put("name", name);
        ArrayNode list = out.putArray("paths");
        int total = 0;
        for (JsonNode p : paths) {
            String d = p.isObject() ? HomeLayoutJson.text(p, "d", null) : null;
            if (d == null || d.isEmpty() || d.length() > TABLER_D_MAX || !PATH_D.matcher(d).matches()) {
                return null;
            }
            total += d.length();
            ObjectNode item = list.addObject().put("d", d);
            for (String paint : List.of("fill", "stroke")) {
                if (p.has(paint)) {
                    String v = HomeLayoutJson.text(p, paint, null);
                    if (v == null || !PATH_PAINTS.contains(v)) {
                        return null;
                    }
                    item.put(paint, v);
                }
            }
            if (p.has("opacity")) {
                String v = HomeLayoutJson.text(p, "opacity", null);
                if (v == null || !OPACITY.matcher(v).matches()) {
                    return null;
                }
                item.put("opacity", v);
            }
        }
        return total > TABLER_TOTAL_MAX ? null : out;
    }

    /** 저장된 올린 아이콘 읽기 — {@code {fileId, url, svg}}(주소는 {@code /} 로 시작하는 사이트 안 주소나 {@code https://}) 모양이 맞으면 사본, 아니면 null */
    public static ObjectNode storedUpload(JsonNode raw) {
        if (raw == null || !raw.isObject()) {
            return null;
        }
        String fileId = HomeLayoutJson.text(raw, "fileId", null);
        String url = HomeLayoutJson.text(raw, "url", null);
        JsonNode svg = raw.get("svg");
        if (fileId == null || !FILE_ID.matcher(fileId).matches() || !publicUrl(url) || svg == null || !svg.isBoolean()) {
            return null;
        }
        return HomeLayoutJson.object().put("fileId", fileId).put("url", url).put("svg", svg.asBoolean());
    }

    /** 공개 그림 주소 모양 — 사이트 안({@code /…}, {@code //} 아님) 또는 {@code https://}, 500자 이하, 공백·따옴표·괄호·역슬래시 없음(CSS {@code url()} 에 넣는다) */
    public static boolean publicUrl(String url) {
        if (url == null || url.length() > 500 || url.startsWith("//")) {
            return false;
        }
        if (!url.startsWith("/") && !url.startsWith("https://")) {
            return false;
        }
        return url.chars().noneMatch(c -> Character.isWhitespace(c) || c == '"' || c == '\'' || c == '(' || c == ')' || c == '\\' || c < 0x20);
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
