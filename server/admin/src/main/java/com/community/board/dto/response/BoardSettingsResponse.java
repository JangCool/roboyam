package com.community.board.dto.response;

import java.time.Instant;

/** 기본 설정 하위 화면 응답(admin-web {@code BoardDefaultsTabResponse}) — 설정 + 마지막 저장(저장한 적 없으면 null) */
public record BoardSettingsResponse<T>(T settings, Instant updatedAt, String updatedBy) {
}
