package com.community.terms.dto.response;

import java.time.Instant;

/** 지금 약관 — {@code body} 는 서버가 정화한 HTML */
public record PublicTermsCurrent(int version, String effectiveDate, Instant publishedAt, String changeNote, String body) {

    @Override
    public String toString() {
        return "PublicTermsCurrent[version=" + version + ", effectiveDate=" + effectiveDate + ", body=" + (body == null ? "null" : body.length() + "자") + "]";
    }
}
