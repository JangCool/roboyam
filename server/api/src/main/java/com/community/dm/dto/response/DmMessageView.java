package com.community.dm.dto.response;

import java.time.Instant;

/** 쪽지 한 통 — user-web {@code DmMessage}(일반 글자, 줄바꿈 그대로) */
public record DmMessageView(String id, boolean mine, String body, Instant createdAt) {

    @Override
    public String toString() {
        return "DmMessageView[id=" + id + ", body=(감춤)]";
    }
}
