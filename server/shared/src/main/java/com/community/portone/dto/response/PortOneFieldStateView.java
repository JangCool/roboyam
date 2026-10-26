package com.community.portone.dto.response;

import java.time.Instant;

/**
 * 포트원 설정 칸 하나의 상태 — 값은 없다(계약 「0-4」 {@code GET /api/admin/portone-settings} 의 {@code fields[]}).
 *
 * @param key        {@code storeId} · {@code apiSecret} · {@code identityChannelKey}
 * @param configured 값이 저장돼 있는가
 * @param updatedAt  마지막으로 저장·지운 순간(한 번도 없으면 null)
 * @param updatedBy  마지막으로 바꾼 운영자 이름(없으면 null)
 */
public record PortOneFieldStateView(String key, boolean configured, Instant updatedAt, String updatedBy) {
}
