package com.community.aimember.dto.response;

/** {@code GET·PUT /api/me/ai-settings} — {@code excludeFromAi}: 「내 글·댓글을 AI 회원에게 보내지 않기」(기본 false = 지금처럼 보낸다) */
public record AiSettingsView(boolean excludeFromAi) {
}
