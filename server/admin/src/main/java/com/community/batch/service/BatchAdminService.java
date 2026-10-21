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
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배치 켜기·끄기, cron 수정, 지금 실행 (docs/features/roles-contract.md 「배치 인프라 결과」). 입구의 권한은 컨트롤러가 선언한다 — 켜기·끄기·cron 은 {@code batch.manage},
 * 지금 실행은 {@code batch.run}(민감 조치). Java 배치와 DB 배치(저장 프로시저 + pg_cron)가 같은 길로 들어와 {@code source} 로 갈린다.
 *
 * <ul>
 *   <li>설정을 바꾸면 감사 기록({@code BATCH_TOGGLE} · {@code BATCH_SCHEDULE_CHANGE})을 남긴다. Java 배치는 커밋 뒤 이 서버의 예약을 곧바로 맞추고(다른 서버는 주기적으로 따라온다),
 *       DB 배치는 {@code cron.alter_job} 으로 pg_cron 에 바로 반영한다(DB 가 실행 주체라 서버를 몇 대 띄웠든 한 곳의 설정이다).</li>
 *   <li>지금 실행은 <b>끝날 때까지 기다린다</b>. 꺼져 있어도 돈다. 이미 실행 중이면 409 {@code BATCH_RUNNING}. 일이 실패해도 요청은 성공(200)이고 결과는 목록의 마지막 실행 상태
 *       ({@code FAIL})와 로그에 있다. 실행은 감사 기록({@code BATCH_RUN})으로 남는다. DB 배치는 같은 저장 프로시저를 {@code CALL} 한다.</li>
 *   <li>DB 배치를 쓸 수 없으면(pg_cron 없음·미등록) 세 동작 모두 409 {@code BATCH_UNAVAILABLE}.</li>
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
    private final DbBatchGateway database;
    private final AuditLogService audit;
    private final ApplicationEventPublisher events;

    public BatchAdminService(BatchJobRepository jobs, BatchCronPolicy cronPolicy, BatchRunner runner, BatchQueryService queries, DbBatchGateway database,
                             AuditLogService audit, ApplicationEventPublisher events) {
        this.jobs = jobs;
        this.cronPolicy = cronPolicy;
        this.runner = runner;
        this.queries = queries;
        this.database = database;
        this.audit = audit;
        this.events = events;
    }

    @Transactional
    public List<BatchJobView> toggle(AdminActor actor, String jobKey, boolean enabled) {
        BatchCatalog definition = definitionOf(jobKey);
        if (!definition.isJava()) {
            return toggleDatabase(actor, definition, enabled);
        }
        BatchJobEntity job = lockedJob(definition);
        if (job.isEnabled() != enabled) {
            boolean was = job.isEnabled();
            job.changeEnabled(enabled, actor.operatorId(), Instant.now());
            recordToggle(actor, definition, was, enabled);
            events.publishEvent(new BatchSettingsChanged(definition.key()));
            log.info("배치 사용 여부를 바꿨다 jobKey={} enabled={} operatorId={}", definition.key(), enabled, actor.operatorId());
        }
        return queries.list();
    }

    @Transactional
    public List<BatchJobView> changeSchedule(AdminActor actor, String jobKey, String schedule) {
        BatchCatalog definition = definitionOf(jobKey);
        if (!definition.isJava()) {
            return changeDatabaseSchedule(actor, definition, schedule);
        }
        String cron = cronPolicy.validate(schedule);
        BatchJobEntity job = lockedJob(definition);
        if (!job.getCron().equals(cron)) {
            String was = job.getCron();
            job.changeCron(cron, actor.operatorId(), Instant.now());
            recordScheduleChange(actor, definition, was, cron);
            events.publishEvent(new BatchSettingsChanged(definition.key()));
            log.info("배치 cron 을 바꿨다 jobKey={} operatorId={}", definition.key(), actor.operatorId());
        }
        return queries.list();
    }

    /** 끝날 때까지 기다려 돌린다. 이미 실행 중이면 {@link BatchRunningException} */
    public List<BatchJobView> runNow(AdminActor actor, String jobKey) {
        BatchCatalog definition = definitionOf(jobKey);
        if (!definition.isJava()) {
            return runDatabaseNow(actor, definition);
        }
        RunReport report = runner.runManual(definition, actor.operatorId());
        if (report.outcome() != Outcome.FINISHED) {
            throw new BatchRunningException();
        }
        recordRun(actor, definition, report.runId(), report.status());
        return queries.list();
    }

    // ── DB 배치 ──────────────────────────────────────────────────────────────

    private List<BatchJobView> toggleDatabase(AdminActor actor, BatchCatalog definition, boolean enabled) {
        DbBatchGateway.DbJob job = queries.usableDbJob(definition);
        if (job.active() != enabled) {
            database.setActive(job.cronJobId(), enabled); // pg_cron 에 바로 반영(트랜잭션 밖) — 아래 목록이 새 값을 읽는다
            recordToggle(actor, definition, job.active(), enabled);
            log.info("DB 배치 사용 여부를 바꿨다 jobKey={} enabled={} operatorId={}", definition.key(), enabled, actor.operatorId());
        }
        return queries.list();
    }

    private List<BatchJobView> changeDatabaseSchedule(AdminActor actor, BatchCatalog definition, String schedule) {
        String cron = cronPolicy.validatePgCron(schedule);
        DbBatchGateway.DbJob job = queries.usableDbJob(definition);
        if (!cron.equals(job.schedule())) {
            try {
                database.setSchedule(job.cronJobId(), cron);
            } catch (DataAccessException e) {
                // 이 서버의 검사를 통과했지만 pg_cron 이 거절한 식(버전별 문법 차이)
                log.warn("pg_cron 이 cron 식을 거절했다 jobKey={}: {}", definition.key(), e.getMostSpecificCause().getMessage());
                throw ValidationException.of("schedule", new MessageRef("batch.schedule.formatDb"));
            }
            recordScheduleChange(actor, definition, job.schedule(), cron);
            log.info("DB 배치 cron 을 바꿨다 jobKey={} operatorId={}", definition.key(), actor.operatorId());
        }
        return queries.list();
    }

    private List<BatchJobView> runDatabaseNow(AdminActor actor, BatchCatalog definition) {
        queries.usableDbJob(definition);
        Long runId;
        try {
            runId = database.call(definition, actor.operatorId());
        } catch (SQLException e) {
            // 프로시저 안에서 처리하지 못한 오류(로그 기록 자체의 실패 등) — 일의 실패는 프로시저가 FAIL 로그로 남기고 여기까지 오지 않는다
            throw new IllegalStateException("DB 배치를 실행하지 못했다: " + definition.key(), e);
        }
        if (runId == null) {
            throw new BatchRunningException(); // 같은 배치가 이미 돌고 있어 프로시저가 아무것도 하지 않았다
        }
        recordRun(actor, definition, runId, queries.runStatus(runId));
        return queries.list();
    }

    // ── 감사 기록 ────────────────────────────────────────────────────────────

    private void recordToggle(AdminActor actor, BatchCatalog definition, boolean was, boolean now) {
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.BATCH_TOGGLE, AuditResult.SUCCESS).target("BATCH", definition.key())
                .with("from", was).with("to", now));
    }

    private void recordScheduleChange(AdminActor actor, BatchCatalog definition, String was, String now) {
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.BATCH_SCHEDULE_CHANGE, AuditResult.SUCCESS).target("BATCH", definition.key())
                .with("from", was).with("to", now));
    }

    private void recordRun(AdminActor actor, BatchCatalog definition, Long runId, BatchRunStatus status) {
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.BATCH_RUN,
                status == BatchRunStatus.FAIL ? AuditResult.FAILURE : AuditResult.SUCCESS).target("BATCH", definition.key())
                .with("runId", String.valueOf(runId)).with("status", status.name()));
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
