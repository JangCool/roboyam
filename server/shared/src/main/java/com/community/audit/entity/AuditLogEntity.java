package com.community.audit.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 감사 기록 한 줄 — V7__operators_roles.sql 의 {@code audit_logs}. 쓰기만 한다(고치거나 지우지 않는다 — 1년 보관 뒤 정리는 후속 배치).
 * 누가({@code actor}) 무엇을({@code action}) 무엇에게({@code target}) 했고 결과({@code result})가 어땠는지를 남긴다.
 * {@code detail} 은 짧은 JSON 문자열(예: 권한 {@code granted}/{@code revoked})이다. <b>비밀번호·토큰은 남기지 않는다.</b>
 * 운영자 행이 지워져도(반려한 신청) 기록은 남도록 {@code actor_id} 에는 외래키를 걸지 않았다.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

    @Id
    private Long id;

    private Instant occurredAt;
    private String actorType;
    private Long actorId;
    private String action;
    private String result;
    private String targetType;
    private String targetId;
    private String ip;
    private String requestId;
    private String detail;

    protected AuditLogEntity() {
    }

    public static AuditLogEntity create(long id, Instant occurredAt, AuditActorType actorType, Long actorId, String action, AuditResult result,
                                        String targetType, String targetId, String ip, String requestId, String detail) {
        AuditLogEntity log = new AuditLogEntity();
        log.id = id;
        log.occurredAt = occurredAt;
        log.actorType = actorType.name();
        log.actorId = actorId;
        log.action = action;
        log.result = result.name();
        log.targetType = targetType;
        log.targetId = targetId;
        log.ip = ip;
        log.requestId = requestId;
        log.detail = detail;
        return log;
    }
}
