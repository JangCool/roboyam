package com.community.dm.dto.request;

/** {@code PUT /api/me/dm/settings} — {@code acceptDm} 이 불리언이 아니면(빠짐 포함) 서비스가 {@code 400 fields.acceptDm} */
public record DmSettingsRequest(Object acceptDm) {
}
