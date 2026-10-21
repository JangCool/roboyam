package com.community.batch.service;

import com.community.zconfiguration.message.MessageRef;
import java.util.Arrays;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 실행 결과 요약의 인자를 DB 칸(JSON 배열 문자열)으로 넣고 꺼낸다. 요약은 문구 키 + 인자로 저장하고 응답 때 요청 언어로 푼다 */
final class BatchMessageCodec {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private BatchMessageCodec() {
    }

    /** 인자가 없으면 null */
    static String encodeArgs(MessageRef ref) {
        if (ref.args().length == 0) {
            return null;
        }
        List<String> args = Arrays.stream(ref.args()).map(String::valueOf).toList();
        return MAPPER.writeValueAsString(args);
    }

    /** 저장된 키와 인자를 문구 참조로 되돌린다. 키가 없으면 null. 인자를 못 읽으면 인자 없이 돌려준다 */
    static MessageRef decode(String key, String args) {
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
}
