package com.community.audit.dto.request;

import java.util.Map;

/**
 * 감사 보관 기간 저장 — {@code days} 에 세 분류(GENERAL · PERSONAL_DATA · MONEY)의 일수를 모두 보낸다. 값은 숫자가 아닐 수 있어 {@code Object} 로 받고 서비스가 검사한다.
 */
public record AuditRetentionSaveRequest(Map<String, Object> days) {
}
