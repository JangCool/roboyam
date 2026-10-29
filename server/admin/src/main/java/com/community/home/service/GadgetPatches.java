package com.community.home.service;

import com.community.zconfiguration.message.MessageRef;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 저장 요청의 가젯 칸 검사 — ⚙ 설정 창의 「내용」({@code config}) · 「모양」({@code style}) · 제목(gadget-page-contract 「가젯 설정」 2026-10-30). 칸·범위의 원천은 {@link GadgetConfigs}·{@link GadgetStyles}.
 * 보낸 칸만 본다 — 검사를 통과한 칸만 담은 덮을 조각을 돌려주고(저장 값의 다른 칸은 그대로), 틀린 칸은 경로({@code gadgets[i].config.<칸>}·{@code gadgets[i].style.<칸>})로 모은다.
 * 칸이 없거나 JSON {@code null} 이면 보내지 않은 것으로 본다. 표에 없는 칸(예: {@code badge}·{@code rank})은 버린다.
 * 칸 사이 규칙(기간 탭은 인기 순일 때만 · 직접 고르기는 게시판 1개 이상)은 저장 값에 덮은 결과로 본다.
 */
final class GadgetPatches {

    /** 직접 고른 게시판 slug 하나의 글자 수 한도 */
    static final int SLUG_MAX = 50;

    private GadgetPatches() {
    }

    /**
     * 제목 — 앞뒤 공백을 떼고 1~40자 · 제어 문자 없음
     * @return 다듬은 제목, 틀리면 null(경로 {@code path.title} 에 모은다)
     */
    static String title(String raw, String path, Map<String, MessageRef> fields) {
        String title = raw == null ? "" : raw.strip();
        if (title.isEmpty() || length(title) > HomeGadgetKinds.TITLE_MAX || title.chars().anyMatch(Character::isISOControl)) {
            fields.put(path + ".title", new MessageRef("homeLayout.gadget.title", String.valueOf(HomeGadgetKinds.TITLE_MAX)));
            return null;
        }
        return title;
    }

    /**
     * 꾸미기 조각
     *
     * @param current 지금 꾸미기(저장 값 · 시드 · 새 가젯이면 테마 기본) — {@code empty} 안쪽 칸을 합칠 때 쓴다
     * @return 덮을 칸(보낸 것이 없거나 틀렸으면 null)
     */
    static ObjectNode style(JsonNode raw, JsonNode current, String path, Map<String, MessageRef> fields) {
        if (raw == null || raw.isNull()) {
            return null;
        }
        String base = path + ".style";
        if (!raw.isObject()) {
            fields.put(base, new MessageRef("homeLayout.gadget.style.option"));
            return null;
        }
        int errors = fields.size();
        ObjectNode out = HomeLayoutJson.object();
        if (raw.has("icon")) {
            JsonNode icon = raw.get("icon");
            if (icon.isNull()) {
                out.putNull("icon");
            } else if (icon.isString() && GadgetStyles.ICON.matcher(icon.asString()).matches()) {
                out.put("icon", icon.asString());
            } else {
                fields.put(base + ".icon", new MessageRef("homeLayout.gadget.style.icon"));
            }
        }
        option(raw, out, "headerStyle", GadgetStyles.HEADER_STYLES, base, "homeLayout.gadget.style.option", fields);
        option(raw, out, "titleSize", GadgetStyles.TITLE_SIZES, base, "homeLayout.gadget.style.option", fields);
        option(raw, out, "cardLook", GadgetStyles.CARD_LOOKS, base, "homeLayout.gadget.style.option", fields);
        option(raw, out, "mobileFold", GadgetStyles.MOBILE_FOLDS, base, "homeLayout.gadget.style.option", fields);
        color(raw, out, "titleColor", base, fields);
        color(raw, out, "cardColor", base, fields);
        if (present(raw, "subtitle")) {
            String subtitle = line(raw.get("subtitle"), GadgetStyles.SUBTITLE_MAX);
            if (subtitle == null) {
                fields.put(base + ".subtitle", new MessageRef("homeLayout.gadget.style.subtitle", String.valueOf(GadgetStyles.SUBTITLE_MAX)));
            } else {
                out.put("subtitle", subtitle);
            }
        }
        if (present(raw, "empty")) {
            JsonNode empty = raw.get("empty");
            if (!empty.isObject()) {
                fields.put(base + ".empty", new MessageRef("homeLayout.gadget.style.option"));
            } else {
                JsonNode was = current == null ? null : current.get("empty");
                ObjectNode e = was != null && was.isObject() ? (ObjectNode) was.deepCopy() : HomeLayoutJson.object();
                for (String flag : List.of("keep", "message")) {
                    if (present(empty, flag)) {
                        if (empty.get(flag).isBoolean()) {
                            e.put(flag, empty.get(flag).asBoolean());
                        } else {
                            fields.put(base + ".empty." + flag, new MessageRef("homeLayout.gadget.style.option"));
                        }
                    }
                }
                if (present(empty, "text")) {
                    String text = line(empty.get("text"), GadgetStyles.EMPTY_TEXT_MAX);
                    if (text == null) {
                        fields.put(base + ".empty.text", new MessageRef("homeLayout.gadget.style.emptyText", String.valueOf(GadgetStyles.EMPTY_TEXT_MAX)));
                    } else {
                        e.put("text", text);
                    }
                }
                out.set("empty", e);
            }
        }
        return fields.size() > errors || out.isEmpty() ? null : out;
    }

    /**
     * 내용 설정 조각 — 그 종류의 표 칸만
     *
     * @param current     지금 설정(저장 값 · 시드 · 새 가젯의 처음 값) — 칸 사이 규칙을 볼 때 쓴다
     * @param place       가젯이 놓인 자리(고를 수 있는 대상 게시판 범위)
     * @param boardExists 바로가기 {@code BOARD} 항목의 게시판 slug 가 있는가
     * @return 덮을 칸(보낸 것이 없거나 틀렸으면 null)
     */
    static ObjectNode config(String type, JsonNode raw, JsonNode current, GadgetConfigs.Place place, Predicate<String> boardExists, String path,
                             Map<String, MessageRef> fields) {
        if (!GadgetConfigs.settable(type) || raw == null || raw.isNull()) {
            return null;
        }
        String base = path + ".config.";
        if (!raw.isObject()) {
            fields.put(path + ".config", new MessageRef("homeLayout.gadget.config.option"));
            return null;
        }
        int errors = fields.size();
        ObjectNode out = HomeLayoutJson.object();
        GadgetConfigs.Count count = GadgetConfigs.count(type);
        if (count != null && present(raw, count.field())) {
            Integer n = integer(raw.get(count.field()));
            if (n == null || n < count.min() || n > count.max()) {
                fields.put(base + count.field(), new MessageRef("perBoard".equals(count.field()) ? "homeLayout.gadget.config.perBoard" : "homeLayout.gadget.config.count",
                        String.valueOf(count.min()), String.valueOf(count.max())));
            } else {
                out.put(count.field(), n);
            }
        }
        List<String> sorts = GadgetConfigs.sorts(type);
        if (!sorts.isEmpty()) {
            option(raw, out, "sort", sorts, base.substring(0, base.length() - 1), "GROUP_BUY".equals(type) ? "homeLayout.gadget.config.sort" : "homeLayout.gadget.config.postSort",
                    fields);
        }
        if (GadgetConfigs.boards(type)) {
            boards(raw, current, place, base, out, fields);
        }
        String opt = "homeLayout.gadget.config.option";
        String at = base.substring(0, base.length() - 1);
        switch (type) {
            case "POST_LIST" -> {
                String sort = out.has("sort") ? out.get("sort").asString() : HomeLayoutJson.text(current, "sort", "LATEST");
                if (present(raw, "periodTabs")) {
                    JsonNode tabs = raw.get("periodTabs");
                    if (!tabs.isBoolean()) {
                        fields.put(base + "periodTabs", new MessageRef(opt));
                    } else if (tabs.asBoolean() && !"POPULAR".equals(sort)) {
                        fields.put(base + "periodTabs", new MessageRef("homeLayout.gadget.config.periodTabs"));
                    } else {
                        out.put("periodTabs", tabs.asBoolean());
                    }
                } else if (out.has("sort") && !"POPULAR".equals(sort)) {
                    out.put("periodTabs", false);
                }
                option(raw, out, "rowStyle", GadgetConfigs.ROW_STYLES, at, opt, fields);
                intOption(raw, out, "columns", List.of(1, 2), base, fields);
                option(raw, out, "thumbnail", GadgetConfigs.THUMBNAILS, at, opt, fields);
                intOption(raw, out, "titleLines", List.of(0, 1, 2), base, fields);
                option(raw, out, "lead", GadgetConfigs.LEADS, at, opt, fields);
                if (present(raw, "meta")) {
                    JsonNode meta = raw.get("meta");
                    Set<String> keys = new LinkedHashSet<>();
                    boolean bad = !meta.isArray();
                    if (!bad) {
                        for (JsonNode m : meta) {
                            if (!m.isString() || !GadgetConfigs.META.contains(m.asString())) {
                                bad = true;
                            } else {
                                keys.add(m.asString());
                            }
                        }
                    }
                    if (bad) {
                        fields.put(base + "meta", new MessageRef(opt));
                    } else {
                        ArrayNode arr = out.putArray("meta");
                        GadgetConfigs.META.stream().filter(keys::contains).forEach(arr::add);
                    }
                }
                bool(raw, out, "dividers", base, fields);
                range(raw, out, "newBadgeHours", 0, GadgetConfigs.NEW_BADGE_MAX, base, fields);
                range(raw, out, "hotBadgeLikes", 0, GadgetConfigs.HOT_BADGE_MAX, base, fields);
            }
            case "BOARD_CARDS" -> {
                bool(raw, out, "cardHead", base, fields);
                if (present(raw, "columns")) {
                    JsonNode c = raw.get("columns");
                    Integer n = integer(c);
                    if (c.isString() && "AUTO".equals(c.asString())) {
                        out.put("columns", "AUTO");
                    } else if (n != null && (n == 2 || n == 3)) {
                        out.put("columns", n);
                    } else {
                        fields.put(base + "columns", new MessageRef(opt));
                    }
                }
            }
            case "HIGHLIGHT" -> {
                option(raw, out, "lead", GadgetConfigs.LEADS, at, opt, fields);
                option(raw, out, "thumbs", GadgetConfigs.HIGHLIGHT_THUMBS, at, opt, fields);
            }
            case "QUICK_LINKS" -> {
                option(raw, out, "shape", GadgetConfigs.QUICK_SHAPES, at, opt, fields);
                if (present(raw, "items")) {
                    quickLinks(raw.get("items"), boardExists, base, out, fields);
                }
            }
            case "NOTICE_TEXT" -> {
                if (present(raw, "text")) {
                    String text = raw.get("text").isString() ? raw.get("text").asString().strip() : null;
                    if (text == null || length(text) > GadgetConfigs.NOTICE_MAX
                            || text.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
                        fields.put(base + "text", new MessageRef("homeLayout.gadget.config.text", String.valueOf(GadgetConfigs.NOTICE_MAX)));
                    } else {
                        out.put("text", text);
                    }
                }
                option(raw, out, "format", GadgetConfigs.NOTICE_FORMATS, at, opt, fields);
            }
            default -> {
            }
        }
        return fields.size() > errors || out.isEmpty() ? null : out;
    }

    /** 대상 게시판 {@code boardScope}·{@code boardIds} — 범위는 그 자리에서 고를 수 있는 것만, 직접 고르기(PICK)는 덮은 결과에 게시판이 1~20개 */
    private static void boards(JsonNode raw, JsonNode current, GadgetConfigs.Place place, String base, ObjectNode out, Map<String, MessageRef> fields) {
        boolean scopeSent = present(raw, "boardScope");
        String scope = null;
        boolean bad = false;
        if (scopeSent) {
            JsonNode s = raw.get("boardScope");
            scope = s.isString() ? s.asString() : null;
            if (scope == null || !GadgetConfigs.SCOPES.contains(scope) || !place.allows(scope)) {
                fields.put(base + "boardScope", new MessageRef("homeLayout.gadget.config.boardScope"));
                bad = true;
            }
        }
        List<String> slugs = null;
        String idsMessage = "homeLayout.gadget.config.boardIds";
        if (present(raw, "boardIds")) {
            JsonNode ids = raw.get("boardIds");
            Set<String> picked = new LinkedHashSet<>();
            boolean badIds = !ids.isArray();
            if (!badIds) {
                for (JsonNode v : ids) {
                    String slug = v.isString() ? v.asString().strip() : "";
                    if (slug.isEmpty() || slug.length() > SLUG_MAX) {
                        badIds = true;
                    } else {
                        picked.add(slug);
                    }
                }
            }
            if (badIds || picked.size() > GadgetConfigs.BOARD_PICK_MAX) {
                fields.put(base + "boardIds", new MessageRef(idsMessage, String.valueOf(GadgetConfigs.BOARD_PICK_MAX)));
                bad = true;
            } else {
                slugs = List.copyOf(picked);
            }
        }
        if (bad) {
            return;
        }
        String effective = scopeSent ? scope : GadgetConfigs.scope(current);
        if (GadgetConfigs.PICK.equals(effective)) {
            List<String> ids = slugs != null ? slugs : HomeLayoutJson.texts(current, "boardIds");
            if (ids.isEmpty()) {
                fields.put(base + "boardIds", new MessageRef(idsMessage, String.valueOf(GadgetConfigs.BOARD_PICK_MAX)));
                return;
            }
            if (scopeSent) {
                out.put("boardScope", scope);
            }
            if (slugs != null || scopeSent) {
                ArrayNode arr = out.putArray("boardIds");
                ids.forEach(arr::add);
            }
        } else if (scopeSent || slugs != null) {
            if (scopeSent) {
                out.put("boardScope", scope);
            }
            out.putArray("boardIds");
        }
    }

    /** 바로가기 항목 0~12개 — 이름 1~12자 · 종류 · 값({@code BOARD} 있는 게시판 slug, {@code PATH} 사이트 안 경로, 나머지 null) */
    private static void quickLinks(JsonNode raw, Predicate<String> boardExists, String base, ObjectNode out, Map<String, MessageRef> fields) {
        if (!raw.isArray() || raw.size() > GadgetConfigs.QUICK_LINK_MAX) {
            fields.put(base + "items", new MessageRef("homeLayout.gadget.config.items", String.valueOf(GadgetConfigs.QUICK_LINK_MAX)));
            return;
        }
        int errors = fields.size();
        ArrayNode items = HomeLayoutJson.array();
        for (int j = 0; j < raw.size(); j++) {
            JsonNode it = raw.get(j);
            String at = base + "items[" + j + "].";
            if (!it.isObject()) {
                fields.put(at + "kind", new MessageRef("homeLayout.gadget.config.option"));
                continue;
            }
            String label = it.path("label").isString() ? it.get("label").asString().strip() : "";
            if (label.isEmpty() || length(label) > GadgetConfigs.QUICK_LABEL_MAX || label.chars().anyMatch(Character::isISOControl)) {
                fields.put(at + "label", new MessageRef("homeLayout.gadget.config.linkLabel", String.valueOf(GadgetConfigs.QUICK_LABEL_MAX)));
            }
            String kind = it.path("kind").isString() ? it.get("kind").asString() : null;
            if (kind == null || !GadgetConfigs.QUICK_KINDS.contains(kind)) {
                fields.put(at + "kind", new MessageRef("homeLayout.gadget.config.option"));
                continue;
            }
            String value = null;
            if ("BOARD".equals(kind) || "PATH".equals(kind)) {
                value = it.path("value").isString() ? it.get("value").asString().strip() : "";
                boolean ok = "BOARD".equals(kind) ? !value.isEmpty() && value.length() <= SLUG_MAX && boardExists.test(value) : GadgetConfigs.isSitePath(value);
                if (!ok) {
                    fields.put(at + "value", new MessageRef("BOARD".equals(kind) ? "homeLayout.gadget.config.linkBoard" : "homeLayout.gadget.config.linkPath"));
                }
            }
            ObjectNode item = items.addObject().put("label", label).put("kind", kind);
            if (value == null) {
                item.putNull("value");
            } else {
                item.put("value", value);
            }
        }
        if (fields.size() == errors) {
            out.set("items", items);
        }
    }

    // ── 칸 하나 ─────────────────────────────────────────────────────────────

    /** 칸이 있고 JSON null 이 아닌가(보낸 칸) */
    private static boolean present(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull();
    }

    private static void option(JsonNode raw, ObjectNode out, String field, List<String> values, String base, String message, Map<String, MessageRef> fields) {
        if (!present(raw, field)) {
            return;
        }
        JsonNode v = raw.get(field);
        if (v.isString() && values.contains(v.asString())) {
            out.put(field, v.asString());
        } else {
            fields.put(base + "." + field, new MessageRef(message));
        }
    }

    private static void intOption(JsonNode raw, ObjectNode out, String field, List<Integer> values, String base, Map<String, MessageRef> fields) {
        if (!present(raw, field)) {
            return;
        }
        Integer n = integer(raw.get(field));
        if (n != null && values.contains(n)) {
            out.put(field, n);
        } else {
            fields.put(base + field, new MessageRef("homeLayout.gadget.config.option"));
        }
    }

    private static void range(JsonNode raw, ObjectNode out, String field, int min, int max, String base, Map<String, MessageRef> fields) {
        if (!present(raw, field)) {
            return;
        }
        Integer n = integer(raw.get(field));
        if (n != null && n >= min && n <= max) {
            out.put(field, n);
        } else {
            fields.put(base + field, new MessageRef("homeLayout.gadget.config.range", String.valueOf(min), String.valueOf(max)));
        }
    }

    private static void bool(JsonNode raw, ObjectNode out, String field, String base, Map<String, MessageRef> fields) {
        if (!present(raw, field)) {
            return;
        }
        JsonNode v = raw.get(field);
        if (v.isBoolean()) {
            out.put(field, v.asBoolean());
        } else {
            fields.put(base + field, new MessageRef("homeLayout.gadget.config.option"));
        }
    }

    private static void color(JsonNode raw, ObjectNode out, String field, String base, Map<String, MessageRef> fields) {
        if (!raw.has(field)) {
            return;
        }
        JsonNode v = raw.get(field);
        if (v.isNull()) {
            out.putNull(field);
            return;
        }
        String color = v.isString() ? GadgetStyles.color(v.asString()) : null;
        if (color == null) {
            fields.put(base + "." + field, new MessageRef("homeLayout.gadget.style.color"));
        } else {
            out.put(field, color);
        }
    }

    /** 한 줄 글자 — 앞뒤 공백을 떼고 {@code max}자 이하 · 제어 문자 없음. 아니면 null */
    private static String line(JsonNode v, int max) {
        if (!v.isString()) {
            return null;
        }
        String s = v.asString().strip();
        return length(s) > max || s.chars().anyMatch(Character::isISOControl) ? null : s;
    }

    /** 정수 칸 — 정수가 아니거나 int 범위 밖이면 null */
    private static Integer integer(JsonNode v) {
        return v != null && v.isIntegralNumber() && v.canConvertToInt() ? v.asInt() : null;
    }

    private static int length(String s) {
        return s.codePointCount(0, s.length());
    }
}
