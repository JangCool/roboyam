package com.community.terms.dto.response;

import java.time.Instant;

/** 초안 — {@code updatedBy} 는 운영자 표시 이름(없으면 null) */
public record TermsDraftView(String body, Instant updatedAt, String updatedBy) {

    @Override
    public String toString() {
        return "TermsDraftView[body=" + (body == null ? "null" : body.length() + "자") + ", updatedAt=" + updatedAt + "]";
    }
}
