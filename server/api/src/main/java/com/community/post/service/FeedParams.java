package com.community.post.service;

import com.community.zconfiguration.message.MessageRef;
import java.util.Map;

/**
 * 게시판이 섞인 목록(최신글·인기글·통합 검색)의 쿼리 값 읽기 — 틀린 값은 칸 경로({@code size}·{@code cursor}·{@code page})로 {@code fields} 에 모은다(부르는 쪽이
 * 다 모은 뒤 {@code ValidationException} 으로 던진다). 비어 있으면 기본값.
 */
public final class FeedParams {

    public static final int SIZE_DEFAULT = 20;
    public static final int SIZE_MAX = 50;

    private FeedParams() {
    }

    /** 쪽 크기 1~{@value #SIZE_MAX}(기본 {@value #SIZE_DEFAULT}). 틀리면 {@code fields.size} 를 넣고 기본값 */
    public static int size(String raw, Map<String, MessageRef> fields) {
        if (raw == null || raw.isBlank()) {
            return SIZE_DEFAULT;
        }
        Integer value = integer(raw);
        if (value == null || value < 1 || value > SIZE_MAX) {
            fields.put("size", new MessageRef("feed.size.range", SIZE_MAX));
            return SIZE_DEFAULT;
        }
        return value;
    }

    /** 커서(마지막 글 ID). 비면 null. 틀리면 {@code fields.cursor} 를 넣고 null */
    public static Long cursor(String raw, Map<String, MessageRef> fields) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Long id = PostQueryService.parseId(raw);
        if (id == null) {
            fields.put("cursor", new MessageRef("feed.cursor.invalid"));
        }
        return id;
    }

    /** 쪽 번호 1~{@code max}(비면 1). 틀리면 {@code fields.page} 를 넣고 1 */
    public static int page(String raw, int max, Map<String, MessageRef> fields) {
        if (raw == null || raw.isBlank()) {
            return 1;
        }
        Integer value = integer(raw);
        if (value == null || value < 1 || value > max) {
            fields.put("page", new MessageRef("feed.page.range", max));
            return 1;
        }
        return value;
    }

    /** 쪽 번호 1 이상(비면 1, 위 한도 없음). 틀리면 {@code fields.page} 를 넣고 1 */
    public static int pageFrom1(String raw, Map<String, MessageRef> fields) {
        if (raw == null || raw.isBlank()) {
            return 1;
        }
        Integer value = integer(raw);
        if (value == null || value < 1) {
            fields.put("page", new MessageRef("feed.page.min"));
            return 1;
        }
        return value;
    }

    private static Integer integer(String raw) {
        String text = raw.strip();
        if (text.length() > 9) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
