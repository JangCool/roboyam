package com.community.aimember.dto.request;

/** {@code PUT /api/me/ai-settings} — {@code excludeFromAi} 가 불리언이 아니면(빠짐 포함) 서비스가 {@code 400 VALIDATION fields.excludeFromAi} */
public record AiSettingsRequest(Object excludeFromAi) {
}
