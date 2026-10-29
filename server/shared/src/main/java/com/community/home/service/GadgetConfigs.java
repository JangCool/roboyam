package com.community.home.service;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 관리자 ⚙ 설정 창이 고치는 가젯 내용 설정 — 종류·칸·범위·처음 값(docs/features/gadget-page-contract.md 「가젯 설정」, admin-web {@code UtilGadgetConfig} 와 같은 값).
 * 공구({@code count}·{@code sort}) · 글 목록·갤러리·게시판 카드(대상 게시판 {@code boardScope}·{@code boardIds} + 개수 · 글 목록은 정렬·기간 탭).
 *
 * <p>대상 게시판 {@code boardScope}: {@code ALL} 전체 · {@code FOLDER} 가젯 페이지의 기준 폴더 게시판 전체(페이지에서만) · {@code PICK} {@code boardIds}(게시판 slug).
 * 저장 설정에 {@code boardScope} 가 없으면(옛 시드) {@code boardIds} 가 있으면 PICK, 없으면 ALL 로 읽는다.
 */
public final class GadgetConfigs {

    public static final String ALL = "ALL";
    public static final String FOLDER = "FOLDER";
    public static final String PICK = "PICK";
    public static final List<String> SCOPES = List.of(ALL, FOLDER, PICK);
    /** 직접 고를 수 있는 게시판 수 */
    public static final int BOARD_PICK_MAX = 20;
    public static final List<String> POST_LIST_SORTS = List.of("LATEST", "POPULAR", "VIEWS", "LIKES", "COMMENTS");

    /** 종류 → 개수 칸 {이름, 최소, 최대, 처음} — 서버 {@code HomeGadgetService} 가 읽는 범위와 같다 */
    private static final Map<String, Count> COUNTS = Map.of(
            "GROUP_BUY", new Count("count", 1, HomeGadgetKinds.GROUP_BUY_COUNT_MAX, HomeGadgetKinds.GROUP_BUY_COUNT_DEFAULT),
            "POST_LIST", new Count("count", 1, 30, 10),
            "GALLERY", new Count("count", 1, 24, 6),
            "BOARD_CARDS", new Count("perBoard", 1, 10, 4));

    private GadgetConfigs() {
    }

    /** 개수 칸 하나 */
    public record Count(String field, int min, int max, int initial) {
    }

    /** ⚙ 설정을 고칠 수 있는 종류인가 */
    public static boolean settable(String type) {
        return type != null && COUNTS.containsKey(type);
    }

    /** 대상 게시판을 고르는 종류인가 */
    public static boolean boards(String type) {
        return "POST_LIST".equals(type) || "GALLERY".equals(type) || "BOARD_CARDS".equals(type);
    }

    public static Count count(String type) {
        return COUNTS.get(type);
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
     * 관리자 편집기가 보는 설정 — 이 종류의 ⚙ 칸만(저장 값, 없으면 처음 값). 고칠 수 없는 종류는 null.
     */
    public static ObjectNode view(String type, JsonNode config) {
        if (!settable(type)) {
            return null;
        }
        Count count = COUNTS.get(type);
        ObjectNode out = HomeLayoutJson.object();
        out.put(count.field(), HomeLayoutJson.number(config, count.field(), count.initial(), count.min(), count.max()));
        List<String> sorts = sorts(type);
        if (!sorts.isEmpty()) {
            String fallback = "GROUP_BUY".equals(type) ? HomeGadgetKinds.GROUP_BUY_SORT_DEFAULT : "LATEST";
            String sort = HomeLayoutJson.text(config, "sort", fallback);
            out.put("sort", sorts.contains(sort) ? sort : fallback);
        }
        if (boards(type)) {
            String scope = scope(config);
            out.put("boardScope", scope);
            ArrayNode ids = out.putArray("boardIds");
            if (PICK.equals(scope)) {
                HomeLayoutJson.texts(config, "boardIds").forEach(ids::add);
            }
        }
        if ("POST_LIST".equals(type)) {
            out.put("periodTabs", "POPULAR".equals(out.path("sort").asString()) && HomeLayoutJson.flag(config, "periodTabs"));
        }
        return out;
    }
}
