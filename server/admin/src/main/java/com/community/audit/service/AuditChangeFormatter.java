package com.community.audit.service;

import com.community.audit.dto.AuditActions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.json.JsonMapper;

/**
 * 감사 기록의 {@code detail}(작업마다 모양이 다른 짧은 JSON)을 화면의 「바뀐 내용」 목록 {@code [{field, before, after}]} 로 바꾼다 — 화면이 {@code changes} 를 이 모양의 JSON 글자로 기대한다.
 * 기록은 쓰는 쪽이 자유롭게 남기므로 모양별 규칙으로 읽는다(새 작업도 아래 규칙에 맞으면 자동으로 보인다):
 *
 * <ul>
 *   <li>{@code before} · {@code after} (칸 이름 → 값 맵) — 칸마다 전후. 한쪽에만 있는 칸은 만들기·지우기.</li>
 *   <li>{@code from} · {@code to} (맵이 아닌 값 한 쌍) — 한 칸. 칸 이름은 작업마다({@code BATCH_TOGGLE}=enabled, {@code BATCH_SCHEDULE_CHANGE}=schedule, 그 밖에 value).
 *       {@code SITE_DISPLAY_UPDATE} 의 {@code 언어/시간대/시간형식/날짜형식} 한 줄은 네 칸으로 푼다.</li>
 *   <li>값이 {@code {from, to}} 맵이면 그 칸의 전후. {@code 전 -> 후} 글자(사이트 이름·소개)는 둘로 가른다.</li>
 *   <li>목록은 쉼표로 이은 글자 — {@code revoked}·{@code rolesRevoked}·{@code deleted} 는 「없어진 것」(전), 그 밖은 「생긴 것」(후).</li>
 *   <li>그 밖의 값은 후 값 하나. 구분용 조각({@code key})·{@code changed} 목록은 내지 않고, 실패·거부 기록의 {@code reason} 은 칸이 아니라 사유 문구로 쓴다.</li>
 * </ul>
 * 비밀번호·토큰은 기록에 처음부터 남기지 않는다 — 여기서도 값을 가공하지 않고 그대로 옮긴다.
 */
final class AuditChangeFormatter {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** 내지 않는 칸 — 구분용 해시 조각, 다른 칸이 이미 말해 주는 목록 */
    private static final Set<String> HIDDEN = Set.of("key", "changed");
    private static final Set<String> REMOVED_LISTS = Set.of("revoked", "rolesRevoked", "deleted");
    private static final Set<String> ARROW_TEXT = Set.of("siteName", "tagline");
    private static final String ARROW = " -> ";
    private static final List<String> DISPLAY_FIELDS = List.of("language", "zoneId", "timeFormat", "dateFormat");

    /** 바뀐 칸 하나 — 화면 {@code AuditChange} 와 같은 모양 */
    record Change(String field, String before, String after) {
    }

    private AuditChangeFormatter() {
    }

    /** {@code detail} JSON 글자를 읽어 맵으로. 없거나 읽을 수 없으면 빈 맵(옛 기록의 모양이 달라도 조회가 멈추면 안 된다) */
    @SuppressWarnings("unchecked")
    static Map<String, Object> parse(String detail) {
        if (detail == null || detail.isBlank()) {
            return Map.of();
        }
        try {
            Object parsed = MAPPER.readValue(detail, Map.class);
            return parsed instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    /** 바뀐 내용 목록의 JSON 글자. 바뀐 것이 없으면 null */
    static String json(String action, Map<String, Object> detail, boolean failed) {
        List<Change> changes = changes(action, detail, failed);
        return changes.isEmpty() ? null : MAPPER.writeValueAsString(changes);
    }

    static List<Change> changes(String action, Map<String, Object> detail, boolean failed) {
        List<Change> out = new ArrayList<>();
        Object from = detail.get("from");
        Object to = detail.get("to");
        boolean pair = detail.containsKey("from") && detail.containsKey("to") && !(from instanceof Map) && !(to instanceof Map);
        if (pair) {
            addPair(out, action, text(from), text(to));
        }
        if (detail.get("before") instanceof Map<?, ?> || detail.get("after") instanceof Map<?, ?>) {
            addBeforeAfter(out, detail.get("before"), detail.get("after"));
        }
        for (Map.Entry<String, Object> entry : detail.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            boolean consumed = (pair && (key.equals("from") || key.equals("to"))) || ((key.equals("before") || key.equals("after")) && value instanceof Map);
            if (consumed || HIDDEN.contains(key) || (failed && key.equals("reason")) || value == null || (value instanceof Collection<?> list && list.isEmpty())) {
                continue;
            }
            if (value instanceof Map<?, ?> map && map.containsKey("from") && map.containsKey("to")) {
                out.add(new Change(key, text(map.get("from")), text(map.get("to"))));
            } else if (value instanceof String s && ARROW_TEXT.contains(key) && s.contains(ARROW)) {
                int at = s.indexOf(ARROW);
                out.add(new Change(key, blankToNull(s.substring(0, at)), blankToNull(s.substring(at + ARROW.length()))));
            } else if (value instanceof Collection<?> && REMOVED_LISTS.contains(key)) {
                out.add(new Change(key, text(value), null));
            } else {
                out.add(new Change(key, null, text(value)));
            }
        }
        return out;
    }

    private static void addPair(List<Change> out, String action, String before, String after) {
        if (AuditActions.SITE_DISPLAY_UPDATE.equals(action) && before != null && after != null) {
            List<String> was = displayParts(before);
            List<String> now = displayParts(after);
            if (was != null && now != null) {
                for (int i = 0; i < DISPLAY_FIELDS.size(); i++) {
                    if (!was.get(i).equals(now.get(i))) {
                        out.add(new Change(DISPLAY_FIELDS.get(i), was.get(i), now.get(i)));
                    }
                }
                return;
            }
        }
        String field = switch (action) {
            case AuditActions.BATCH_TOGGLE -> "enabled";
            case AuditActions.BATCH_SCHEDULE_CHANGE -> "schedule";
            default -> "value";
        };
        out.add(new Change(field, before, after));
    }

    /**
     * {@code 언어/시간대/시간형식/날짜형식} 한 줄을 네 칸으로 가른다. 시간대 이름({@code Asia/Seoul})에 슬래시가 있어 앞 하나와 뒤 둘을 떼고 나머지를 시간대로 본다.
     * 모양이 다르면 null(가르지 않고 한 칸으로 남긴다).
     */
    private static List<String> displayParts(String line) {
        String[] parts = line.split("/", -1);
        if (parts.length < DISPLAY_FIELDS.size()) {
            return null;
        }
        return List.of(parts[0], String.join("/", Arrays.asList(parts).subList(1, parts.length - 2)), parts[parts.length - 2], parts[parts.length - 1]);
    }

    private static void addBeforeAfter(List<Change> out, Object before, Object after) {
        Map<?, ?> was = before instanceof Map<?, ?> map ? map : Map.of();
        Map<?, ?> now = after instanceof Map<?, ?> map ? map : Map.of();
        Set<Object> fields = new LinkedHashSet<>(was.keySet());
        fields.addAll(now.keySet());
        for (Object field : fields) {
            out.add(new Change(String.valueOf(field), text(was.get(field)), text(now.get(field))));
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object element : list) {
                parts.add(element instanceof Map<?, ?> || element instanceof Collection<?> ? MAPPER.writeValueAsString(element) : String.valueOf(element));
            }
            return String.join(", ", parts);
        }
        if (value instanceof Map<?, ?>) {
            return MAPPER.writeValueAsString(value);
        }
        return String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
