package com.community.groupbuy.service;

import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 2b-3 서비스들이 같이 쓰는 감사 한 줄·운영자 사유 검사(5~200자 — {@code GroupBuyAdminService} 와 같은 규칙). 감사 detail 에 배송지·계좌 평문을 싣지 않는다(10-9).
 */
@Component
public class GroupBuyAudit {

    public static final String TARGET_ORDER = "GROUP_BUY_ORDER";
    public static final String TARGET_REQUEST = "GROUP_BUY_REQUEST";
    public static final String TARGET_GROUP_BUY = "GROUP_BUY";
    /** 공구 문의·리뷰(2b-6) */
    public static final String TARGET_QUESTION = "GROUP_BUY_QUESTION";
    public static final String TARGET_REVIEW = "GROUP_BUY_REVIEW";
    public static final String TARGET_BANK_TRANSFER = "COMMERCE_BANK_TRANSFER";
    public static final String TARGET_CARRIER = "COMMERCE_CARRIER";

    private final AuditLogService audit;

    public GroupBuyAudit(AuditLogService audit) {
        this.audit = audit;
    }

    public void record(Long operatorId, String action, String targetType, long targetId, String ip, Map<String, Object> detail) {
        record(operatorId, action, targetType, Long.toString(targetId), ip, detail);
    }

    public void record(Long operatorId, String action, String targetType, String targetId, String ip, Map<String, Object> detail) {
        audit.record(new AuditEvent(operatorId == null ? AuditActorType.SYSTEM : AuditActorType.OPERATOR, operatorId, action, AuditResult.SUCCESS, targetType, targetId, ip,
                detail));
    }

    /** 운영자 사유 5~200자({@code fields.reason}) */
    public static String reason(String reason) {
        if (reason == null || reason.strip().length() < 5 || reason.strip().length() > 200) {
            throw ValidationException.of("reason", new MessageRef("groupbuy.reason.invalid"));
        }
        return reason.strip();
    }
}
