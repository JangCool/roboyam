package com.community.rewardevent.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.rewardevent.entity.RewardEventEntity;
import com.community.rewardevent.entity.RewardEventMode;
import com.community.rewardevent.entity.RewardEventStatus;
import com.community.rewardevent.entity.RewardLineResultStatus;
import com.community.rewardevent.entity.RewardRecipientStatus;
import com.community.rewardevent.exception.RewardEventNotFoundException;
import com.community.rewardevent.repository.master.RewardEventStore;
import com.community.rewardevent.repository.master.RewardRecipientStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 보상 이벤트 쓰기 흐름의 공통 걸음 — 이벤트 행 잠금 + 「끝 시각이 지난 진행 중」을 마감으로 고정(배치 없음 — 다음 쓰기 때, 감사 {@code REWARD_EVENT_AUTO_CLOSE} cause PERIOD) ·
 * 지급 끝 판정 · 감사 기록(대상 {@code REWARD_EVENT} + 이벤트 ID). 부르는 서비스의 트랜잭션 안에서만.
 */
@Component
public class RewardEventSupport {

    public static final String TARGET = "REWARD_EVENT";

    private final RewardEventStore events;
    private final RewardRecipientStore recipients;
    private final AuditLogService audit;

    public RewardEventSupport(RewardEventStore events, RewardRecipientStore recipients, AuditLogService audit) {
        this.events = events;
        this.recipients = recipients;
        this.audit = audit;
    }

    /** 이벤트 행을 잠그고(첫 문장) 끝 시각이 지난 진행 중을 마감으로 고정한 뒤 지금 값을 읽는다 — 없으면 404 */
    public RewardEventEntity lockFresh(long eventId, Instant now) {
        if (!events.lock(eventId)) {
            throw new RewardEventNotFoundException();
        }
        if (events.closeExpired(eventId, now)) {
            record(null, null, AuditActions.REWARD_EVENT_AUTO_CLOSE, eventId, Map.of("cause", "PERIOD"));
            settleDone(eventId, now);
        }
        return events.event(eventId).orElseThrow(RewardEventNotFoundException::new);
    }

    /**
     * 지급 끝 판정 — 마감이고, 나눠 처리 중인 실행이 없고, (선착순이거나 지급 실행이 있었고), 대기 중인 받는 사람·전달 대기 줄이 없으면 DONE(실패가 남아도 — 「실패만 다시 지급」은
     * DONE 에서도 된다). 바뀌었으면 true.
     */
    public boolean settleDone(long eventId, Instant now) {
        RewardEventEntity event = events.event(eventId).orElse(null);
        if (event == null || event.getStatus() != RewardEventStatus.CLOSED) {
            return false;
        }
        if (events.runningRun(eventId).isPresent()) {
            return false;
        }
        if (event.getMode() != RewardEventMode.FIRST_COME && events.grantedEvents(List.of(eventId)).isEmpty()) {
            return false;
        }
        if (recipients.countByStatus(eventId, RewardRecipientStatus.PENDING) > 0 || manualPending(eventId) > 0) {
            return false;
        }
        return events.done(eventId, now);
    }

    private long manualPending(long eventId) {
        return recipients.stats(List.of(eventId)).get(eventId).manualPending();
    }

    /** 감사 한 줄 — 운영자가 없으면 시스템(자동 마감 등) */
    public void record(Long operatorId, String ip, String action, long eventId, Map<String, Object> detail) {
        AuditEvent event = operatorId == null
                ? new AuditEvent(AuditActorType.SYSTEM, null, action, AuditResult.SUCCESS, TARGET, Long.toString(eventId), null, detail)
                : AuditEvent.byOperator(operatorId, ip, action, AuditResult.SUCCESS).target(TARGET, Long.toString(eventId));
        if (operatorId != null) {
            for (Map.Entry<String, Object> e : detail.entrySet()) {
                event = event.with(e.getKey(), e.getValue());
            }
        }
        audit.record(event);
    }

    /** 줄 결과 상태가 아직 지급 전인가 */
    public static boolean pending(RewardLineResultStatus status) {
        return status == RewardLineResultStatus.PENDING;
    }
}
