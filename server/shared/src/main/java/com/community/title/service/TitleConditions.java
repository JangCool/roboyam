package com.community.title.service;

import com.community.title.dto.TitleCondition;
import com.community.title.entity.TitleConditionType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 칭호 조건 JSON({@code titles.conditions}) 읽고 쓰기 — {@code [{type, value?, boardId?, boardSlug?, gradeKey?}]}. 게시판 ID 는 JSON 안에서도 글자다(Snowflake — 자바스크립트 수 범위 밖).
 * 모르는 종류·깨진 칸은 읽을 때 건너뛴다(운영자가 저장한 값만 들어오므로 생기지 않지만 판정이 멈추지 않게).
 */
public final class TitleConditions {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private TitleConditions() {
    }

    public static List<TitleCondition> read(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        JsonNode root = MAPPER.readTree(json);
        if (root == null || !root.isArray()) {
            return List.of();
        }
        List<TitleCondition> out = new ArrayList<>();
        for (JsonNode node : root) {
            TitleConditionType type = TitleConditionType.parse(text(node, "type")).orElse(null);
            if (type == null) {
                continue;
            }
            JsonNode value = node.get("value");
            Integer number = value == null || value.isNull() || !value.canConvertToInt() ? null : value.asInt();
            Long boardId = null;
            String boardText = text(node, "boardId");
            if (boardText != null && boardText.matches("[0-9]{1,19}")) {
                boardId = Long.parseLong(boardText);
            }
            out.add(new TitleCondition(type, number, boardId, text(node, "boardSlug"), text(node, "gradeKey")));
        }
        return List.copyOf(out);
    }

    public static String write(List<TitleCondition> conditions) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TitleCondition c : conditions == null ? List.<TitleCondition>of() : conditions) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", c.type().name());
            if (c.value() != null) {
                row.put("value", c.value());
            }
            if (c.boardId() != null) {
                row.put("boardId", Long.toString(c.boardId()));
            } else if (c.boardSlug() != null) {
                row.put("boardSlug", c.boardSlug());
            }
            if (c.gradeKey() != null) {
                row.put("gradeKey", c.gradeKey());
            }
            rows.add(row);
        }
        return MAPPER.writeValueAsString(rows);
    }

    private static String text(JsonNode node, String name) {
        JsonNode v = node.get(name);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.isString() ? v.asString() : v.toString();
        return s.isBlank() ? null : s.strip();
    }
}
