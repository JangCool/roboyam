package com.community.block.dto.response;

/** {@code PUT /api/users/{id}/memo} — user-web {@code MemoResult}. 지웠으면 null */
public record MemoResultView(String memo) {
}
