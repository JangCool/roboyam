package com.community.home.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.node.ObjectNode;

/**
 * 홈 가젯 종류 8종과 종류별 최소 크기 — admin-web {@code UtilHomeLayoutEditor.KINDS}(12칸 기준 minW·minH, 24칸이면 폭 ×2)와 같은 값.
 * 로보얌에 없는 핫딜·직접홍보·광고는 없다. 격자 고정값(행 높이 15px · 간격 16px, 높이 최대 {@value #MAX_H}행, 위치 최대 {@value #MAX_Y}행)은 공유 레이어 {@code UtilHomeLayout} 과 같다.
 */
public final class HomeGadgetKinds {

    public static final List<Integer> COLUMNS = List.of(12, 24);
    public static final int MAX_Y = 2000;
    public static final int MAX_H = 200;
    /** 한 테마의 가젯 수 한도 */
    public static final int MAX_GADGETS = 30;
    /** 가젯 제목 글자 수 */
    public static final int TITLE_MAX = 40;

    /** 종류 → {minW, minH}(12칸 기준) */
    private static final Map<String, int[]> MIN = minSizes();

    private HomeGadgetKinds() {
    }

    public static boolean isKnown(String type) {
        return type != null && MIN.containsKey(type);
    }

    public static List<String> types() {
        return List.copyOf(MIN.keySet());
    }

    /** 그 칸 수에서의 최소 폭 */
    public static int minW(String type, int columns) {
        int[] min = MIN.getOrDefault(type, new int[]{2, 2});
        return Math.min(columns, min[0] * (columns / 12));
    }

    public static int minH(String type) {
        return MIN.getOrDefault(type, new int[]{2, 2})[1];
    }

    /**
     * 테마의 기본 꾸미기 — 다음식·네이버식은 카드 + 막대 머리, 기본·로보얌은 카드 없는 구역(user-web 목업 {@code baseStyle} 과 같다). 새로 넣은 가젯이 이것으로 시작한다.
     */
    public static ObjectNode baseStyle(String theme) {
        boolean news = "daum".equals(theme) || "naver".equals(theme);
        ObjectNode empty = HomeLayoutJson.object().put("keep", true).put("message", true).put("text", "");
        ObjectNode style = HomeLayoutJson.object();
        style.putNull("icon");
        style.put("headerStyle", news ? "BAR" : "PLAIN").put("titleSize", "M");
        style.putNull("titleColor");
        style.put("subtitle", "").put("cardLook", news ? "DEFAULT" : "FLAT");
        style.putNull("cardColor");
        style.put("mobileFold", "NONE");
        style.set("empty", empty);
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
        min.put("QUICK_LINKS", new int[]{3, 2});
        min.put("NOTICE_TEXT", new int[]{2, 3});
        return java.util.Collections.unmodifiableMap(min);
    }
}
