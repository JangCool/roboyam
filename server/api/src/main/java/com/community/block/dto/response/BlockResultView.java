package com.community.block.dto.response;

import java.time.Instant;

/** {@code PUT /api/users/{id}/block} — user-web {@code BlockResult}. 이미 차단했으면 처음 차단한 시각 */
public record BlockResultView(boolean blocked, Instant blockedAt) {
}
