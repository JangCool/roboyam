package com.community.batch.core;

import com.community.zconfiguration.message.MessageRef;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 배치 기록의 글 칸을 JSON 으로 넣고 꺼낸다 — 결과 요약의 인자(JSON 배열 문자열, 요약은 문구 키 + 인자로 저장하고 응답 때 요청 언어로 푼다), 실행 파라미터(JSON 객체),
 * 고른 단계(JSON 배열). DB 쪽 {@code batch_args}(R__batch_00_helpers.sql)와 같은 모양이다.
 */
public final class BatchMessageCodec {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private BatchMessageCodec() {
    }

    /** 인자가 없으면 null */
    public static String encodeArgs(MessageRef ref) {
        if (ref.args().length == 0) {
            return null;
        }
        List<String> args = Arrays.stream(ref.args()).map(String::valueOf).toList();
        return MAPPER.writeValueAsString(args);
    }

    /** 저장된 키와 인자를 문구 참조로 되돌린다. 키가 없으면 null. 인자를 못 읽으면 인자 없이 돌려준다 */
    public static MessageRef decode(String key, String args) {
        if (key == null) {
            return null;
        }
        if (args == null) {
            return new MessageRef(key);
        }
        try {
            List<String> values = MAPPER.readValue(args, new TypeReference<List<String>>() {
            });
            return new MessageRef(key, values.toArray());
        } catch (RuntimeException e) {
            return new MessageRef(key);
        }
    }

    /** 실행 파라미터 → JSON 객체(이름순). 비어 있으면 null */
    public static String encodeParams(Map<String, String> params) {
        return params == null || params.isEmpty() ? null : MAPPER.writeValueAsString(new java.util.TreeMap<>(params));
    }

    /** JSON 객체 → 실행 파라미터. 없거나 못 읽으면 빈 맵 */
    public static Map<String, String> decodeParams(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return new LinkedHashMap<>(MAPPER.readValue(json, new TypeReference<Map<String, String>>() {
            }));
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    /** 고른 단계 → JSON 배열. 모두 돌리면(null) null */
    public static String encodeSteps(List<String> steps) {
        return steps == null ? null : MAPPER.writeValueAsString(steps);
    }

    /** JSON 배열 → 고른 단계. 없으면 null(모두) */
    public static List<String> decodeSteps(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return List.copyOf(MAPPER.readValue(json, new TypeReference<List<String>>() {
            }));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
