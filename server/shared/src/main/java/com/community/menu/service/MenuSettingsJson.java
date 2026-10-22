package com.community.menu.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 항목 설정({@code settings} jsonb) ↔ JSON 노드. 모르는 유형의 설정을 받은 그대로 보존하려고 맵이 아니라 JSON 노드로 다룬다 */
public final class MenuSettingsJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private MenuSettingsJson() {
    }

    public static JsonNode read(String json) {
        return json == null || json.isBlank() ? null : MAPPER.readTree(json);
    }

    /** null·JSON null 은 null(칸을 비운다) */
    public static String write(JsonNode node) {
        return node == null || node.isNull() ? null : MAPPER.writeValueAsString(node);
    }

    /** {@code PATH}·{@code URL} 의 정리된 설정 — {@code {<addressKey>: 주소, newTab}} */
    public static JsonNode address(String addressKey, String address, boolean newTab) {
        return MAPPER.createObjectNode().put(addressKey, address).put("newTab", newTab);
    }

    /** 설정의 글자 칸(없거나 글자가 아니면 null) */
    public static String text(JsonNode settings, String field) {
        JsonNode value = settings == null ? null : settings.get(field);
        return value != null && value.isString() ? value.asString() : null;
    }

    /** 설정의 참/거짓 칸(없거나 불리언이 아니면 false) */
    public static boolean flag(JsonNode settings, String field) {
        JsonNode value = settings == null ? null : settings.get(field);
        return value != null && value.isBoolean() && value.asBoolean();
    }
}
