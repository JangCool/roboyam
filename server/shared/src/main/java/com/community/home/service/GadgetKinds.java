package com.community.home.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 가젯 종류 8종 + 광고 2종({@code AD}·{@code AD_TEXT_LINE} — ad-contract 「공개 노출 목업」 확정 2026-11-01)과 종류별 최소 크기 — admin-web {@code UtilGadgetLayoutEditor.KINDS}
 * (12칸 기준 minW·minH, 24칸이면 폭 ×2)와 같은 값. 아이로빗에 없는 핫딜·직접홍보는 없다. 광고 가젯은 공개 응답의 {@code data} 가 null 이다(화면이 {@code GET /api/ads} 로 채운다). 격자 고정값(행 높이 15px · 간격 16px, 높이 최대 {@value #MAX_H}행, 위치 최대 {@value #MAX_Y}행)은 공유 레이어 {@code UtilHomeLayout} 과 같다.
 */
public final class GadgetKinds {

    public static final List<Integer> COLUMNS = List.of(12, 24);
    public static final int MAX_Y = 2000;
    public static final int MAX_H = 200;
    /** 한 테마의 가젯 수 한도 */
    public static final int MAX_GADGETS = 30;
    /** 가젯 제목 글자 수 */
    public static final int TITLE_MAX = 40;

    /** 공구 가젯({@code GROUP_BUY}) 설정 — 보일 개수 1~{@value #GROUP_BUY_COUNT_MAX}(기본 {@value #GROUP_BUY_COUNT_DEFAULT}) · 정렬(사용자 결정 2026-10-29, 관리자 ⚙ 설정 창) */
    public static final int GROUP_BUY_COUNT_DEFAULT = 5;
    public static final int GROUP_BUY_COUNT_MAX = 10;
    /** 마감 임박 순(기본 — 연장 중이면 연장 끝) · 새로 시작한 순(시작 시각 늦은 순) · 참여 많은 순(참여 수량, 같으면 마감 이른 순) */
    public static final List<String> GROUP_BUY_SORTS = List.of("DEADLINE", "NEWEST", "POPULAR");
    public static final String GROUP_BUY_SORT_DEFAULT = "DEADLINE";

    /**
     * 게시판 레이아웃의 고정 칸 「게시판 본문」(board-layout-contract) — 8종에 들지 않는다(홈·가젯 페이지에서는 모르는 종류). 꼭 하나, 최소 12칸 기준 폭 {@value #BOARD_BODY_MIN_W} · 높이
     * {@value #BOARD_BODY_MIN_H}(24칸이면 폭 ×2), ⚙·꾸미기 없음, 제목 고정. admin-web {@code UtilBoardLayout.BODY}·{@code UtilGadgetLayoutEditor.FIXED_KINDS} 와 같은 값.
     */
    public static final String BOARD_BODY = "BOARD_BODY";
    public static final int BOARD_BODY_MIN_W = 6;
    public static final int BOARD_BODY_MIN_H = 20;

    /**
     * 핫딜 가젯(회원 핫딜 1차 — hotdeal-contract 「데이터 모양」, 서버 2단계에서 종류 목록에 넣었다) — 최소 크기는 공구 가젯과 같다(12칸 기준 3×8, admin-web
     * {@code UtilGadgetLayoutEditor.KINDS}). 설정 {@code boards}(핫딜 게시판 slug, 비우면 모두 — 일반 대상 게시판 {@code boardScope} 와 다른 종류만의 칸) · {@code sort} ·
     * {@code count} 1~{@value #HOT_DEALS_COUNT_MAX}(기본 {@value #HOT_DEALS_COUNT_DEFAULT}). 공개 데이터는 진행 중(ACTIVE — 의심·종료·기간 종료 제외)·공개 글만.
     */
    public static final String HOT_DEALS = "HOT_DEALS";
    public static final int HOT_DEALS_COUNT_DEFAULT = 5;
    public static final int HOT_DEALS_COUNT_MAX = 10;
    /** 최신(기본) · 인기 · 마감 임박 */
    public static final List<String> HOT_DEALS_SORTS = List.of("LATEST", "POPULAR", "ENDING_SOON");
    public static final String HOT_DEALS_SORT_DEFAULT = "LATEST";

    /** 광고 배너 가젯 — {@code config.adType}(배너 넷, 처음 {@code WIDE_BANNER} — 날개 배너는 가젯에 없다) */
    public static final String AD = "AD";
    /** 한 줄 광고 가젯 — {@code config.count} 1~5(처음 3) */
    public static final String AD_TEXT_LINE = "AD_TEXT_LINE";

    /**
     * 팁·강좌·사용기 매거진({@code /tips}) 가젯 4종(tips-contract 「/tips 레이아웃 편집」 2026-11-13) — 시스템 페이지 {@code TIPS}({@code gadget_pages.system_page})의 배치에서만 받는다
     * (홈·게시판 레이아웃·일반 가젯 페이지에서는 모르는 종류 — {@link #isKnown} 은 거짓). 내용 설정({@code config})이 없고 공개 응답의 {@code data} 는 null 이다
     * (화면이 {@code /api/tips}·{@code /api/tips/posts} 로 채운다). 최소 크기는 계약 표(12칸 기준)와 같다.
     */
    public static final String TIPS_HEADLINE = "TIPS_HEADLINE";
    public static final String TIPS_LATEST = "TIPS_LATEST";
    public static final String TIPS_POPULAR = "TIPS_POPULAR";
    public static final String TIPS_TAGS = "TIPS_TAGS";

    /** 종류 → 머리 아이콘(아이로빗 테마의 새 가젯) — 매거진 4종은 지금 /tips 화면의 머리 아이콘과 같다 */
    private static final Map<String, String> ICONS = Map.ofEntries(Map.entry("HIGHLIGHT", "pi-sparkles"), Map.entry("POST_LIST", "pi-list"),
            Map.entry("BOARD_CARDS", "pi-th-large"), Map.entry("GALLERY", "pi-images"), Map.entry("GROUPS", "pi-users"), Map.entry("GROUP_BUY", "pi-shopping-bag"),
            Map.entry(HOT_DEALS, "pi-ticket"), Map.entry("QUICK_LINKS", "pi-link"), Map.entry("NOTICE_TEXT", "pi-megaphone"), Map.entry(AD, "pi-image"),
            Map.entry(AD_TEXT_LINE, "pi-bars"), Map.entry(TIPS_HEADLINE, "pi-star"), Map.entry(TIPS_LATEST, "pi-clock"), Map.entry(TIPS_POPULAR, "pi-eye"),
            Map.entry(TIPS_TAGS, "pi-hashtag"));

    /** 종류 → {minW, minH}(12칸 기준) */
    private static final Map<String, int[]> MIN = minSizes();
    /** 매거진 가젯 → {minW, minH}(12칸 기준) — {@link #MIN} 과 따로 두어 {@link #isKnown}·{@link #types} 에 들지 않는다 */
    private static final Map<String, int[]> TIPS_MIN = tipsMinSizes();

    private GadgetKinds() {
    }

    /** 8종 + 광고 2종 중 하나인가 — 게시판 본문은 아니다 */
    public static boolean isKnown(String type) {
        return type != null && MIN.containsKey(type);
    }

    public static List<String> types() {
        return List.copyOf(MIN.keySet());
    }

    /** 팁·강좌·사용기 매거진 가젯(시스템 페이지 TIPS 의 배치에서만 받는다)인가 */
    public static boolean isTips(String type) {
        return type != null && TIPS_MIN.containsKey(type);
    }

    /** 매거진 가젯 4종(차례 = 머리 · 최신 · 많이 본 · 태그) */
    public static List<String> tipsTypes() {
        return List.copyOf(TIPS_MIN.keySet());
    }

    /** 그 칸 수에서의 최소 폭 */
    public static int minW(String type, int columns) {
        return Math.min(columns, min(type)[0] * (columns / 12));
    }

    public static int minH(String type) {
        return min(type)[1];
    }

    /** 종류의 {minW, minH}(12칸 기준) — 게시판 본문 · 8종 + 광고 · 매거진 4종, 모르면 2×2 */
    private static int[] min(String type) {
        if (BOARD_BODY.equals(type)) {
            return new int[]{BOARD_BODY_MIN_W, BOARD_BODY_MIN_H};
        }
        int[] tips = type == null ? null : TIPS_MIN.get(type);
        return tips != null ? tips : MIN.getOrDefault(type, new int[]{2, 2});
    }

    /** 공구 가젯 설정의 보일 개수 — 없거나 정수가 아니면 기본, 1~{@value #GROUP_BUY_COUNT_MAX} 로 자른다 */
    public static int groupBuyCount(JsonNode config) {
        return HomeLayoutJson.number(config, "count", GROUP_BUY_COUNT_DEFAULT, 1, GROUP_BUY_COUNT_MAX);
    }

    /** 공구 가젯 설정의 정렬 — 없거나 모르는 값이면 마감 임박 순 */
    public static String groupBuySort(JsonNode config) {
        String sort = HomeLayoutJson.text(config, "sort", GROUP_BUY_SORT_DEFAULT);
        return GROUP_BUY_SORTS.contains(sort) ? sort : GROUP_BUY_SORT_DEFAULT;
    }

    /** 핫딜 가젯 설정의 보일 개수 — 없거나 정수가 아니면 기본, 1~{@value #HOT_DEALS_COUNT_MAX} 로 자른다 */
    public static int hotDealsCount(JsonNode config) {
        return HomeLayoutJson.number(config, "count", HOT_DEALS_COUNT_DEFAULT, 1, HOT_DEALS_COUNT_MAX);
    }

    /** 핫딜 가젯 설정의 정렬 — 없거나 모르는 값이면 최신 */
    public static String hotDealsSort(JsonNode config) {
        String sort = HomeLayoutJson.text(config, "sort", HOT_DEALS_SORT_DEFAULT);
        return HOT_DEALS_SORTS.contains(sort) ? sort : HOT_DEALS_SORT_DEFAULT;
    }

    /** 핫딜 가젯 설정의 핫딜 게시판 slug — 글자만, 겹침 없이 고른 차례로 {@value GadgetConfigs#BOARD_PICK_MAX}개까지(비면 모든 핫딜 게시판) */
    public static List<String> hotDealsBoards(JsonNode config) {
        return HomeLayoutJson.texts(config, "boards").stream().map(String::strip).filter(s -> !s.isEmpty()).distinct().limit(GadgetConfigs.BOARD_PICK_MAX).toList();
    }

    /**
     * 테마의 기본 꾸미기 — 다음식·네이버식은 카드 + 막대 머리, 아이로빗은 카드 + 장식 없는 머리(홈 아이로빗 시드와 같은 흰 카드), 기본은 카드 없는 구역.
     */
    public static ObjectNode baseStyle(String theme) {
        boolean news = "daum".equals(theme) || "naver".equals(theme);
        boolean card = news || "irobit".equals(theme);
        ObjectNode empty = HomeLayoutJson.object().put("keep", true).put("message", true).put("text", "");
        ObjectNode style = HomeLayoutJson.object();
        style.putNull("icon");
        style.put("headerStyle", news ? "BAR" : "PLAIN").put("titleSize", "M");
        style.putNull("titleColor");
        style.put("subtitle", "").put("cardLook", card ? "DEFAULT" : "FLAT");
        style.putNull("cardColor");
        style.put("mobileFold", "NONE");
        style.put("bottomSpace", "NORMAL"); // 카드 아래 여백(home-news-contract — 처음 NORMAL)
        style.set("empty", empty);
        // 제목 아이콘(gadget-page-contract 「가젯 제목 아이콘」 2026-11-10) — 모든 테마 보이기(아이로빗도 — 사용자 2026-11-10 「아이로빗 기본 보이기」), 바탕은 아이로빗 옅은 네모 · 그 밖 없음
        boolean irobit = "irobit".equals(theme);
        style.put("iconShow", true).put("iconSource", GadgetStyles.ICON_PRIME);
        style.putNull("iconTabler");
        style.putNull("iconUpload");
        style.putNull("iconColor");
        style.put("iconBg", irobit ? "SOFT" : "NONE");
        return style;
    }

    /**
     * 새로 넣은 가젯의 처음 꾸미기 — 테마 기본 꾸미기에, 아이로빗이면 종류 아이콘을 더한다(홈 아이로빗 시드처럼. 아이콘은 admin-web {@code UtilGadgetLayoutEditor.KINDS} 와 같다).
     * 아이로빗은 이 아이콘이 처음부터 보인다(사용자 2026-11-10 「아이로빗 기본 보이기」).
     */
    public static ObjectNode baseStyle(String theme, String type) {
        ObjectNode style = baseStyle(theme);
        String icon = ICONS.get(type);
        if ("irobit".equals(theme) && icon != null) {
            style.put("icon", icon);
        }
        return kindStyle(style, type);
    }

    /** 광고 가젯({@code AD}·{@code AD_TEXT_LINE})인가 */
    public static boolean isAd(String type) {
        return AD.equals(type) || AD_TEXT_LINE.equals(type);
    }

    /**
     * 종류만의 처음 꾸미기를 테마 기본 위에 덮는다 — 광고 가젯은 머리 숨김(「광고」 표시가 제목 노릇) · 비면 접기, 배너({@code AD})는 평평(그림 자체가 면).
     * admin-web {@code UtilGadgetStyle} 의 {@code HEADERLESS_KINDS}·{@code FLAT_KINDS} 와 같다. 다른 종류는 그대로 돌려준다.
     */
    public static ObjectNode kindStyle(ObjectNode style, String type) {
        if (!isAd(type)) {
            return style;
        }
        style.put("headerStyle", "HIDDEN");
        if (AD.equals(type)) {
            style.put("cardLook", "FLAT");
        }
        if (style.get("empty") instanceof ObjectNode empty) {
            empty.put("keep", false);
        }
        return style;
    }

    private static Map<String, int[]> minSizes() {
        Map<String, int[]> min = new LinkedHashMap<>();
        min.put("HIGHLIGHT", new int[]{4, 8});
        min.put("POST_LIST", new int[]{3, 6});
        min.put("BOARD_CARDS", new int[]{4, 8});
        min.put("GALLERY", new int[]{3, 6});
        min.put("GROUPS", new int[]{3, 6});
        min.put("GROUP_BUY", new int[]{3, 8});
        min.put(HOT_DEALS, new int[]{3, 8});
        min.put("QUICK_LINKS", new int[]{3, 2});
        min.put("NOTICE_TEXT", new int[]{2, 3});
        min.put(AD, new int[]{2, 2});
        min.put(AD_TEXT_LINE, new int[]{3, 2});
        return java.util.Collections.unmodifiableMap(min);
    }

    private static Map<String, int[]> tipsMinSizes() {
        Map<String, int[]> min = new LinkedHashMap<>();
        min.put(TIPS_HEADLINE, new int[]{6, 10});
        min.put(TIPS_LATEST, new int[]{4, 10});
        min.put(TIPS_POPULAR, new int[]{3, 6});
        min.put(TIPS_TAGS, new int[]{3, 3});
        return java.util.Collections.unmodifiableMap(min);
    }
}
