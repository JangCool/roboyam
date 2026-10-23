package com.community.notificationtemplate.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 특수 상황 규칙 하나 — 「조건 이름 = 값」 이면 이 문구. {@code notification_message_selections.rules} 배열의 원소 모양({@code {id, key, value, messageId}})이다.
 *
 * @param id        규칙 id(Snowflake 글자 — 화면이 줄을 가리킬 때 쓴다)
 * @param key       조건 이름({@code MessageCondition} 상수)
 * @param value     조건 값
 * @param messageId 쓸 문구 id(같은 대상 · 채널의 것 — 언어와 상관없이)
 */
public record MessageRule(String id, String key, String value, String messageId) {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** 저장된 JSON 을 읽는다. 읽을 수 없으면 빈 목록(옛 줄의 모양이 달라도 발송·조회가 멈추면 안 된다) */
    public static List<MessageRule> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> rows = MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
            List<MessageRule> out = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                out.add(new MessageRule(text(row.get("id")), text(row.get("key")), text(row.get("value")), text(row.get("messageId"))));
            }
            return List.copyOf(out);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    public static String write(List<MessageRule> rules) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MessageRule rule : rules) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rule.id());
            row.put("key", rule.key());
            row.put("value", rule.value());
            row.put("messageId", rule.messageId());
            rows.add(row);
        }
        return MAPPER.writeValueAsString(rows);
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
