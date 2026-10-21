package com.community.batch.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.batch.catalog.BatchCatalog;
import com.community.batch.dto.response.BatchJobView;
import com.community.batch.entity.BatchJobEntity;
import com.community.batch.entity.BatchRunStatus;
import com.community.batch.exception.BatchNotFoundException;
import com.community.batch.exception.BatchRunningException;
import com.community.batch.repository.master.BatchJobRepository;
import com.community.batch.service.BatchRunner.Outcome;
import com.community.batch.service.BatchRunner.RunReport;
import com.community.permission.dto.AdminActor;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배치 켜기·끄기, cron 수정, 지금 실행 (docs/features/roles-contract.md 「배치 인프라 결과」). 입구의 권한은 컨트롤러가 선언한다 — 켜기·끄기·cron 은 {@code batch.manage},
 * 지금 실행은 {@code batch.run}(민감 조치).
 *
 * <ul>
 *   <li>설정을 바꾸면 감사 기록({@code BATCH_TOGGLE} · {@code BATCH_SCHEDULE_CHANGE})을 남기고, 커밋 뒤 이 서버의 예약을 곧바로 맞춘다(다른 서버는 주기적으로 따라온다).</li>
 *   <li>지금 실행은 <b>끝날 때까지 기다린다</b>. 꺼져 있어도 돈다. 이미 실행 중이면 409 {@code BATCH_RUNNING}. 일이 실패해도 요청은 성공(200)이고 결과는 목록의 마지막 실행 상태
 *       ({@code FAIL})와 로그에 있다. 실행은 감사 기록({@code BATCH_RUN})으로 남는다.</li>
 *   <li>세 동작 모두 바뀐 배치 목록 전체를 돌려준다.</li>
 * </ul>
 */
@Service
public class BatchAdminService {

    private static final Logger log = LoggerFactory.getLogger(BatchAdminService.class);

    private final BatchJobRepository jobs;
    private final BatchCronPolicy cronPolicy;
    private final BatchRunner runner;
    private final BatchQueryService queries;
    private final AuditLogService audit;
    private final ApplicationEventPublisher events;

    public BatchAdminService(BatchJobRepository jobs, BatchCronPolicy cronPolicy, BatchRunner runner, BatchQueryService queries, AuditLogService audit,
                             ApplicationEventPublisher events) {
        this.jobs = jobs;
        this.cronPolicy = cronPolicy;
        this.runner = runner;
        this.queries = queries;
        this.audit = audit;
        this.events = events;
    }

    @Transactional
    public List<BatchJobView> toggle(AdminActor actor, String jobKey, boolean enabled) {
        BatchCatalog definition = definitionOf(jobKey);
        BatchJobEntity job = lockedJob(definition);
        if (job.isEnabled() != enabled) {
            boolean was = job.isEnabled();
            job.changeEnabled(enabled, actor.operatorId(), Instant.now());
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.BATCH_TOGGLE, AuditResult.SUCCESS).target("BATCH", definition.key())
                    .with("from", was).with("to", enabled));
            events.publishEvent(new BatchSettingsChanged(definition.key()));
            log.info("배치 사용 여부를 바꿨다 jobKey={} enabled={} operatorId={}", definition.key(), enabled, actor.operatorId());
        }
        return queries.list();
    }

    @Transactional
    public List<BatchJobView> changeSchedule(AdminActor actor, String jobKey, String schedule) {
        BatchCatalog definition = definitionOf(jobKey);
        String cron = cronPolicy.validate(schedule);
        BatchJobEntity job = lockedJob(definition);
        if (!job.getCron().equals(cron)) {
            String was = job.getCron();
            job.changeCron(cron, actor.operatorId(), Instant.now());
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.BATCH_SCHEDULE_CHANGE, AuditResult.SUCCESS).target("BATCH", definition.key())
                    .with("from", was).with("to", cron));
            events.publishEvent(new BatchSettingsChanged(definition.key()));
            log.info("배치 cron 을 바꿨다 jobKey={} operatorId={}", definition.key(), actor.operatorId());
        }
        return queries.list();
    }

    /** 끝날 때까지 기다려 돌린다. 이미 실행 중이면 {@link BatchRunningException} */
    public List<BatchJobView> runNow(AdminActor actor, String jobKey) {
        BatchCatalog definition = definitionOf(jobKey);
        RunReport report = runner.runManual(definition, actor.operatorId());
        if (report.outcome() != Outcome.FINISHED) {
            throw new BatchRunningException();
        }
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.BATCH_RUN,
                report.status() == BatchRunStatus.FAIL ? AuditResult.FAILURE : AuditResult.SUCCESS).target("BATCH", definition.key())
                .with("runId", String.valueOf(report.runId())).with("status", report.status().name()));
        return queries.list();
    }

    private BatchJobEntity lockedJob(BatchCatalog definition) {
        return jobs.findWithLockByJobKey(definition.key()).orElseThrow(() -> new IllegalStateException("batch_jobs 행이 없다: " + definition.key()));
    }

    private static BatchCatalog definitionOf(String jobKey) {
        BatchCatalog definition = BatchCatalog.ofKey(jobKey);
        if (definition == null) {
            throw new BatchNotFoundException();
        }
        return definition;
    }
}
