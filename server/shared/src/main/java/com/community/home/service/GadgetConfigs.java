package com.community.home.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 관리자 ⚙ 설정 창(「내용」)이 고치는 가젯 내용 설정 — 8종 전부의 칸·범위·처음 값(docs/features/gadget-page-contract.md 「가젯 설정」 표, admin-web {@code UtilGadgetConfig} ·
 * 공개 읽기 user-web {@code UtilHomeGadget} 과 같은 값). 검사는 관리자 서버 {@code GadgetPatches}, 공개 데이터는 {@code HomeGadgetService} 가 같은 범위로 읽는다.
 *
 * <ul>
 *   <li>{@code GROUP_BUY} {@code count} 1~10(5) · {@code sort} · {@code POST_LIST} 대상 게시판 · {@code count} 1~30(10) · {@code sort} · {@code periodTabs} · 보기 모양
 *       {@code rowStyle}·{@code columns} 1·2·{@code thumbnail}·{@code titleLines} 0·1·2·{@code lead}·{@code meta}·{@code dividers}·{@code newBadgeHours} 0~72·{@code hotBadgeLikes} 0~1000 ·
 *       {@code GALLERY} 대상 게시판 · {@code count} 1~24(6) · {@code BOARD_CARDS} 대상 게시판 · {@code perBoard} 1~10(4) · {@code cardHead} · {@code columns} AUTO·2·3 ·
 *       {@code HIGHLIGHT} {@code count} 1~6(5) · {@code lead} · {@code thumbs} · {@code GROUPS} {@code count} 1~6(3) · {@code QUICK_LINKS} {@code shape} · {@code items} 0~12 ·
 *       {@code NOTICE_TEXT} {@code text} 0~300 · {@code format}.</li>
 *   <li>대상 게시판 {@code boardScope}: {@code ALL} 전체 · {@code FOLDER} 가젯 페이지의 기준 폴더 게시판 전체(페이지에서만) · {@code BOARD} 지금 보는 게시판(게시판 레이아웃에서만) ·
 *       {@code PICK} {@code boardIds}(게시판 slug). 저장 설정에 {@code boardScope} 가 없으면(옛 시드) {@code boardIds} 가 있으면 PICK, 없으면 ALL 로 읽는다.</li>
 * </ul>
 */
public final class GadgetConfigs {

    public static final String ALL = "ALL";
    public static final String FOLDER = "FOLDER";
    public static final String PICK = "PICK";
    public static final String BOARD = "BOARD";
    public static final List<String> SCOPES = List.of(ALL, FOLDER, PICK, BOARD);
    /** 직접 고를 수 있는 게시판 수 */
    public static final int BOARD_PICK_MAX = 20;
    public static final List<String> POST_LIST_SORTS = List.of("LATEST", "POPULAR", "VIEWS", "LIKES", "COMMENTS");
    public static final List<String> ROW_STYLES = List.of("HEADLINE", "RANK", "ARTICLE", "COMPACT");
    public static final List<String> THUMBNAILS = List.of("AUTO", "NONE", "RIGHT", "LEFT");
    public static final List<String> META = List.of("BOARD", "PREFIX", "COMMENTS", "TIME", "AUTHOR");
    /** 글 줄 정보 처음 값 — user-web {@code DEFAULT_META} */
    public static final List<String> DEFAULT_META = List.of("BOARD", "PREFIX", "COMMENTS", "TIME");
    /** 글 목록 큰 글(처음 NONE) · 하이라이트 큰 글(처음 HERO) */
    public static final List<String> LEADS = List.of("NONE", "HERO");
    public static final List<String> HIGHLIGHT_THUMBS = List.of("ALL", "FIRST", "NONE");
    public static final List<String> QUICK_SHAPES = List.of("PILL", "TAB");
    public static final List<String> QUICK_KINDS = List.of("ALL", "POPULAR", "LATEST", "GROUPS", "GROUP_BUY", "BOARD", "PATH");
    public static final List<String> NOTICE_FORMATS = List.of("PLAIN", "BULLETS");
    public static final int NEW_BADGE_MAX = 72;
    public static final int HOT_BADGE_MAX = 1000;
    public static final int QUICK_LINK_MAX = 12;
    public static final int QUICK_LABEL_MAX = 12;
    public static final int NOTICE_MAX = 300;
    /** 사이트 안 경로 글자 수 한도 — user-web {@code safePath} */
    public static final int PATH_MAX = 300;

    private static final Pattern SCHEME = Pattern.compile("^/[a-zA-Z][a-zA-Z0-9+.-]*:.*", Pattern.DOTALL);

    /** 종류 → 개수 칸 {이름, 최소, 최대, 처음} — 공개 {@code HomeGadgetService} 가 읽는 범위와 같다 */
    private static final Map<String, Count> COUNTS = Map.of(
            "GROUP_BUY", new Count("count", 1, HomeGadgetKinds.GROUP_BUY_COUNT_MAX, HomeGadgetKinds.GROUP_BUY_COUNT_DEFAULT),
            "POST_LIST", new Count("count", 1, 30, 10),
            "GALLERY", new Count("count", 1, 24, 6),
            "BOARD_CARDS", new Count("perBoard", 1, 10, 4),
            "HIGHLIGHT", new Count("count", 1, 6, 5),
            "GROUPS", new Count("count", 1, 6, 3));

    private GadgetConfigs() {
    }

    /** 개수 칸 하나 */
    public record Count(String field, int min, int max, int initial) {
    }

    /** 가젯이 놓인 자리 — 고를 수 있는 대상 게시판 범위가 다르다({@code FOLDER} 는 가젯 페이지에서만, {@code BOARD} 는 게시판 레이아웃에서만) */
    public enum Place {
        HOME, PAGE, BOARD;

        /** 이 자리에서 고를 수 있는 범위인가 */
        public boolean allows(String scope) {
            if (FOLDER.equals(scope)) {
                return this == PAGE;
            }
            if (GadgetConfigs.BOARD.equals(scope)) {
                return this == BOARD;
            }
            return ALL.equals(scope) || PICK.equals(scope);
        }

        /** 새로 넣은 게시판 가젯의 처음 범위 — admin-web {@code UtilGadgetConfig.scopes(place)[0]} */
        public String initialScope() {
            return switch (this) {
                case PAGE -> FOLDER;
                case BOARD -> GadgetConfigs.BOARD;
                default -> ALL;
            };
        }
    }

    /** ⚙ 설정을 고칠 수 있는 종류인가 — 8종 전부(게시판 본문은 아니다) */
    public static boolean settable(String type) {
        return HomeGadgetKinds.isKnown(type);
    }

    /** 대상 게시판을 고르는 종류인가 */
    public static boolean boards(String type) {
        return "POST_LIST".equals(type) || "GALLERY".equals(type) || "BOARD_CARDS".equals(type);
    }

    /** 개수 칸(없는 종류 — 바로가기 · 안내 글 — 는 null) */
    public static Count count(String type) {
        return type == null ? null : COUNTS.get(type);
    }

    /** 정렬 목록(정렬 칸이 없는 종류는 빈 목록) */
    public static List<String> sorts(String type) {
        return switch (type) {
            case "GROUP_BUY" -> HomeGadgetKinds.GROUP_BUY_SORTS;
            case "POST_LIST" -> POST_LIST_SORTS;
            default -> List.of();
        };
    }

    /** 저장 설정의 대상 게시판 범위 */
    public static String scope(JsonNode config) {
        String scope = HomeLayoutJson.text(config, "boardScope", null);
        if (scope != null && SCOPES.contains(scope)) {
            return scope;
        }
        return HomeLayoutJson.texts(config, "boardIds").isEmpty() ? ALL : PICK;
    }

    /**
     * 관리자 편집기가 보는 설정 — 이 종류의 표 칸 전부(저장 값, 없거나 모르는 값이면 처음 값 — admin-web {@code UtilGadgetConfig.normalize} 와 같은 읽기). 고칠 수 없는 종류(게시판 본문)는 null.
     */
    public static ObjectNode view(String type, JsonNode config) {
        if (!settable(type)) {
            return null;
        }
        ObjectNode out = HomeLayoutJson.object();
        Count count = COUNTS.get(type);
        if (count != null) {
            out.put(count.field(), HomeLayoutJson.number(config, count.field(), count.initial(), count.min(), count.max()));
        }
        List<String> sorts = sorts(type);
        if (!sorts.isEmpty()) {
            String fallback = "GROUP_BUY".equals(type) ? HomeGadgetKinds.GROUP_BUY_SORT_DEFAULT : "LATEST";
            out.put("sort", oneOf(config, "sort", sorts, fallback));
        }
        if (boards(type)) {
            String scope = scope(config);
            out.put("boardScope", scope);
            ArrayNode ids = out.putArray("boardIds");
            if (PICK.equals(scope)) {
                HomeLayoutJson.texts(config, "boardIds").stream().distinct().limit(BOARD_PICK_MAX).forEach(ids::add);
            }
        }
        switch (type) {
            case "POST_LIST" -> {
                out.put("periodTabs", "POPULAR".equals(out.path("sort").asString()) && HomeLayoutJson.flag(config, "periodTabs"));
                out.put("rowStyle", oneOf(config, "rowStyle", ROW_STYLES, "HEADLINE"));
                out.put("columns", intOrDigit(config, "columns") == 2 ? 2 : 1);
                out.put("thumbnail", oneOf(config, "thumbnail", THUMBNAILS, "AUTO"));
                int lines = intOrDigit(config, "titleLines");
                out.put("titleLines", lines == 1 || lines == 2 ? lines : 0);
                out.put("lead", "HERO".equals(HomeLayoutJson.text(config, "lead", null)) ? "HERO" : "NONE");
                ArrayNode meta = out.putArray("meta");
                JsonNode rawMeta = config == null ? null : config.get("meta");
                if (rawMeta != null && rawMeta.isArray()) {
                    List<String> sent = HomeLayoutJson.texts(config, "meta");
                    META.stream().filter(sent::contains).forEach(meta::add);
                } else {
                    DEFAULT_META.forEach(meta::add);
                }
                out.put("dividers", bool(config, "dividers", true));
                out.put("newBadgeHours", HomeLayoutJson.number(config, "newBadgeHours", 0, 0, NEW_BADGE_MAX));
                out.put("hotBadgeLikes", HomeLayoutJson.number(config, "hotBadgeLikes", 0, 0, HOT_BADGE_MAX));
            }
            case "BOARD_CARDS" -> {
                out.put("cardHead", cardHead(config));
                int columns = intOrDigit(config, "columns");
                if (columns == 2 || columns == 3) {
                    out.put("columns", columns);
                } else {
                    out.put("columns", "AUTO");
                }
            }
            case "HIGHLIGHT" -> {
                out.put("lead", "NONE".equals(HomeLayoutJson.text(config, "lead", null)) ? "NONE" : "HERO");
                out.put("thumbs", oneOf(config, "thumbs", HIGHLIGHT_THUMBS, "ALL"));
            }
            case "QUICK_LINKS" -> {
                out.put("shape", oneOf(config, "shape", QUICK_SHAPES, "PILL"));
                ArrayNode items = out.putArray("items");
                JsonNode raw = config == null ? null : config.get("items");
                if (raw != null && raw.isArray()) {
                    for (JsonNode it : raw) {
                        String kind = HomeLayoutJson.text(it, "kind", null);
                        if (kind == null || !QUICK_KINDS.contains(kind) || items.size() >= QUICK_LINK_MAX) {
                            continue;
                        }
                        ObjectNode item = items.addObject().put("label", cut(HomeLayoutJson.text(it, "label", ""), QUICK_LABEL_MAX)).put("kind", kind);
                        if ("BOARD".equals(kind) || "PATH".equals(kind)) {
                            item.put("value", HomeLayoutJson.text(it, "value", ""));
                        } else {
                            item.putNull("value");
                        }
                    }
                }
            }
            case "NOTICE_TEXT" -> {
                out.put("text", cut(HomeLayoutJson.text(config, "text", ""), NOTICE_MAX));
                out.put("format", oneOf(config, "format", NOTICE_FORMATS, "PLAIN"));
            }
            default -> {
            }
        }
        return out;
    }

    /** 게시판 카드의 카드 머리(로고·이름·「게시판 가기」)를 보이는가 — 칸이 없거나 불리언이 아니면 보인다 */
    public static boolean cardHead(JsonNode config) {
        JsonNode value = config == null ? null : config.get("cardHead");
        return value == null || !value.isBoolean() || value.asBoolean();
    }

    /** 이 사이트 안 경로인가 — {@code /} 로 시작하고 {@code //}·스킴·공백·제어 문자·역슬래시가 없고 {@value #PATH_MAX}자 이하(user-web {@code safePath} 와 같다) */
    public static boolean isSitePath(String value) {
        if (value == null || !value.startsWith("/") || value.startsWith("//") || value.length() > PATH_MAX) {
            return false;
        }
        if (value.chars().anyMatch(c -> Character.isWhitespace(c) || c == '\\' || c < 0x20)) {
            return false;
        }
        return !SCHEME.matcher(value).matches();
    }

    private static String oneOf(JsonNode config, String field, List<String> values, String fallback) {
        String v = HomeLayoutJson.text(config, field, null);
        return v != null && values.contains(v) ? v : fallback;
    }

    private static boolean bool(JsonNode config, String field, boolean fallback) {
        JsonNode value = config == null ? null : config.get(field);
        return value != null && value.isBoolean() ? value.asBoolean() : fallback;
    }

    /** 정수 또는 숫자 한 글자 칸(user-web 이 {@code 2}·{@code '2'} 를 같이 읽는다) — 아니면 -1 */
    private static int intOrDigit(JsonNode config, String field) {
        JsonNode value = config == null ? null : config.get(field);
        if (value == null) {
            return -1;
        }
        if (value.isIntegralNumber() && value.canConvertToInt()) {
            return value.asInt();
        }
        if (value.isString() && value.asString().matches("[0-9]")) {
            return Integer.parseInt(value.asString());
        }
        return -1;
    }

    private static String cut(String value, int max) {
        return value.codePointCount(0, value.length()) > max ? value.substring(0, value.offsetByCodePoints(0, max)) : value;
    }
}
