package com.community.audit.dto;

/**
 * 감사 기록이 업무 트랜잭션 안에서 남았다 — {@code AuditLogService#record} 가 같은 스레드·같은 트랜잭션에서 알린다(Spring 애플리케이션 이벤트).
 * 관리자 저장 뒤 공개 캐시를 비우는 쪽({@code CacheAutoRefresh}, 관리자 서버)이 듣는다. 따로 남기는 기록({@code recordIsolated} — 거절 등)은 알리지 않는다.
 */
public record AuditRecorded(AuditEvent event) {
}
