package com.community.commerce.guard.service;

import com.community.commerce.guard.dto.RequestAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 멱등 키의 요청 해시 — SHA-256(요청 종류 ‖ 줄바꿈 ‖ 본문의 정렬한 JSON) 16진 64자. 본문은 칸 이름 순으로 정렬해 쓰므로 칸 차례만 다른 같은 본문은 같은 해시다.
 * 본문이 없으면 {@code null} JSON 으로 본다.
 */
public final class RequestHashes {

    private static final JsonMapper CANONICAL = JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();

    private RequestHashes() {
    }

    /** @param body 요청 본문(DTO·맵·JSON 트리 — 무엇이든 JSON 으로 바꿀 수 있는 값) */
    public static String of(RequestAction action, Object body) {
        Object plain = body == null ? null : CANONICAL.convertValue(body, Object.class);
        String json = CANONICAL.writeValueAsString(plain);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(action.name().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
