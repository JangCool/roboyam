package com.community.block.dto.request;

/** {@code POST /api/me/blocks/words} — 검사(2~20자·한 줄)는 서비스가 한다 */
public record BlockedWordRequest(String word) {
}
