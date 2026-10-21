package com.community.notification.service;

import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 알림 문구의 인자를 DB 칸(JSON 배열 글자)으로 넣고 꺼내 읽는 사람의 언어로 푼다. 원소는 글자이거나 문구 참조 {@code {"key":"문구 키","args":["…"]}} 다 — 배치 이름이나
 * 「역할이 추가됐어요」 같은 문장은 저장할 때가 아니라 <b>읽을 때</b> 읽는 사람의 언어로 풀려야 하기 때문이다(발행은 행위자의 요청 스레드에서 일어나 언어가 수신자와 다를 수 있다).
 * 문구 키는 메시지 번들({@code messages_ko·en.properties})에 있다.
 */
public final class NotificationArgs {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** 인자 글자 하나의 최대 길이 — 오류 요약 같은 긴 글이 알림 행을 키우지 않게 자른다 */
    static final int MAX_ARG_LENGTH = 300;

    private NotificationArgs() {
    }

    /** 인자를 JSON 배열 글자로. 인자가 없으면 null */
    public static String encode(List<Object> args) {
        if (args == null || args.isEmpty()) {
            return null;
        }
        List<Object> out = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof MessageRef ref) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("key", ref.key());
                if (ref.args().length > 0) {
                    node.put("args", java.util.Arrays.stream(ref.args()).map(NotificationArgs::text).toList());
                }
                out.add(node);
            } else {
                out.add(text(arg));
            }
        }
        return MAPPER.writeValueAsString(out);
    }

    /**
     * 저장된 인자를 {@code locale} 의 문구 인자로 푼다. 읽을 수 없는 값은 인자 없이 돌려준다(예전 기록의 모양이 달라도 조회가 멈추면 안 된다).
     */
    public static Object[] resolve(String json, Messages messages, Locale locale) {
        if (json == null || json.isBlank()) {
            return new Object[0];
        }
        List<Object> nodes;
        try {
            nodes = MAPPER.readValue(json, new TypeReference<List<Object>>() {
            });
        } catch (RuntimeException e) {
            return new Object[0];
        }
        Object[] out = new Object[nodes.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = resolveOne(nodes.get(i), messages, locale);
        }
        return out;
    }

    /** 문구 키 + 저장된 인자를 읽는 사람의 언어 문구로. 본문이 없는 알림(빈 문구)은 null */
    public static String text(String key, String argsJson, Messages messages, Locale locale) {
        String resolved = messages.get(new MessageRef(key, resolve(argsJson, messages, locale)), locale);
        return resolved == null || resolved.isBlank() ? null : resolved;
    }

    private static Object resolveOne(Object node, Messages messages, Locale locale) {
        if (node instanceof Map<?, ?> map && map.get("key") instanceof String key) {
            Object[] nested = map.get("args") instanceof List<?> list ? list.stream().map(NotificationArgs::text).toArray() : new Object[0];
            return messages.get(new MessageRef(key, nested), locale);
        }
        return node == null ? "" : String.valueOf(node);
    }

    private static String text(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return text.length() <= MAX_ARG_LENGTH ? text : text.substring(0, MAX_ARG_LENGTH - 1) + "…";
    }
}
