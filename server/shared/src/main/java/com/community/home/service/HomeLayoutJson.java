package com.community.home.service;

import com.community.home.dto.HomeLayoutItem;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** 홈 구성의 JSON 칸({@code home_layouts.items}·{@code home_gadgets.style|config|more} jsonb) ↔ 값. 꾸미기·설정은 모르는 칸도 보존하려고 JSON 노드로 다룬다 */
public final class HomeLayoutJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private HomeLayoutJson() {
    }

    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode array() {
        return MAPPER.createArrayNode();
    }

    public static JsonNode read(String json) {
        return json == null || json.isBlank() ? null : MAPPER.readTree(json);
    }

    /** null·JSON null 은 null */
    public static String write(JsonNode node) {
        return node == null || node.isNull() ? null : MAPPER.writeValueAsString(node);
    }

    public static String writeItems(List<HomeLayoutItem> items) {
        ArrayNode out = array();
        for (HomeLayoutItem it : items) {
            out.add(object().put("gadgetId", it.gadgetId()).put("x", it.x()).put("y", it.y()).put("w", it.w()).put("h", it.h()));
        }
        return MAPPER.writeValueAsString(out);
    }

    public static List<HomeLayoutItem> readItems(String json) {
        List<HomeLayoutItem> out = new ArrayList<>();
        JsonNode node = read(json);
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode it : node) {
            out.add(new HomeLayoutItem(it.path("gadgetId").asString(), it.path("x").asInt(), it.path("y").asInt(), it.path("w").asInt(), it.path("h").asInt()));
        }
        return out;
    }

    /** 설정의 글자 칸(없거나 글자가 아니면 기본값) */
    public static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isString() ? value.asString() : fallback;
    }

    /** 설정의 정수 칸(없거나 정수가 아니면 기본값, {@code min}~{@code max} 로 자른다) */
    public static int number(JsonNode node, String field, int fallback, int min, int max) {
        JsonNode value = node == null ? null : node.get(field);
        int n = value != null && value.isIntegralNumber() ? value.asInt() : fallback;
        return Math.min(max, Math.max(min, n));
    }

    /** 설정의 참/거짓 칸(없거나 불리언이 아니면 false) */
    public static boolean flag(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isBoolean() && value.asBoolean();
    }

    /** 설정의 글자 목록 칸(없으면 빈 목록, 글자가 아닌 값은 건너뛴다) */
    public static List<String> texts(JsonNode node, String field) {
        List<String> out = new ArrayList<>();
        JsonNode value = node == null ? null : node.get(field);
        if (value != null && value.isArray()) {
            for (JsonNode v : value) {
                if (v.isString()) {
                    out.add(v.asString());
                }
            }
        }
        return out;
    }
}
