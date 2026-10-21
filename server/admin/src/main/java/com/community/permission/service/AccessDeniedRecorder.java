package com.community.permission.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.permission.dto.AdminActor;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 권한 거부를 감사 기록으로 남긴다({@code PERMISSION_DENIED}, 결과 {@code DENIED}). 거부는 예외로 요청이 끝나 업무 트랜잭션이 롤백되는 일이므로
 * <b>따로 커밋되는 트랜잭션</b>으로 쓴다({@link AuditLogService#recordIsolated}). 기록하다 실패해도 거부 응답(403)은 그대로 나가야 하므로 던지지 않고 로그만 남긴다.
 */
@Component
public class AccessDeniedRecorder {

    private static final Logger log = LoggerFactory.getLogger(AccessDeniedRecorder.class);

    private final AuditLogService audit;

    public AccessDeniedRecorder(AuditLogService audit) {
        this.audit = audit;
    }

    /**
     * @param reason     왜 거부됐나(예: {@code MISSING_PERMISSION} · {@code ESCALATION} · {@code SUPER_ONLY} · {@code UNDECLARED})
     * @param targetType 대상 종류({@code ENDPOINT} · {@code ROLE} …)
     * @param targetId   대상 식별자(엔드포인트면 {@code 메서드 경로})
     * @param required   필요했던 권한 코드들(없으면 빈 목록)
     */
    public void record(AdminActor actor, String reason, String targetType, String targetId, List<String> required) {
        try {
            AuditEvent event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.PERMISSION_DENIED, AuditResult.DENIED)
                    .target(targetType, targetId).with("reason", reason);
            if (!required.isEmpty()) {
                event = event.with("required", required);
            }
            audit.recordIsolated(event);
        } catch (RuntimeException e) {
            log.error("권한 거부 감사 기록을 남기지 못했다 operatorId={} 원인={}", actor.operatorId(), e.getClass().getSimpleName());
        }
    }
}
