package com.community.block.dto.response;

import java.time.Instant;

/** 차단 단어 한 줄 — user-web {@code BlockedWordView} */
public record BlockedWordView(String id, String word, Instant createdAt) {
}
