package com.community.audit.dto.response;

import java.util.List;

/** 감사 로그 한 쪽 — {@code page} 는 0 부터. 발생 시각 최근순 */
public record AuditPage(List<AuditLogView> items, long total, int page, int size) {
}
