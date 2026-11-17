package com.community.trial.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.trial.dto.TrialViews;
import com.community.trial.service.TrialService.Actor;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 광고주 화면에서 회원 개인정보(받는 분·연락처·주소)를 본 기록(trial-group-contract 「구현 결정」 14 — 개인정보 제공 감사, 개인정보 보관 분류). 응답에 실린 줄이 있을 때만 한 건.
 */
@Component
public class TrialProvisionLog {

    private final AuditLogService audit;

    public TrialProvisionLog(AuditLogService audit) {
        this.audit = audit;
    }

    public void viewed(Actor actor, TrialViews.TrialDetail detail) {
        List<String> shown = detail.participants().stream().filter(p -> p.recipient() != null || p.phone() != null).map(TrialViews.Participant::id).toList();
        if (shown.isEmpty()) {
            return;
        }
        boolean address = detail.participants().stream().anyMatch(p -> p.address1() != null);
        audit.record(AuditEvent.byMember(actor.id(), actor.ip(), AuditActions.TRIAL_PII_PROVIDE, AuditResult.SUCCESS).target("TRIAL", detail.id())
                .with("advertiserId", Long.toString(actor.advertiserId())).with("participantIds", shown)
                .with("fields", address ? List.of("NAME", "PHONE", "ADDRESS") : List.of("NAME", "PHONE")).with("cause", "ADVERTISER_VIEW"));
    }
}
