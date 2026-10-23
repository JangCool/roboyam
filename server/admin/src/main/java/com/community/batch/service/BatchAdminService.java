package com.community.batch.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchMessageCodec;
import com.community.batch.core.BatchParameterValidator;
import com.community.batch.core.BatchRegistry;
import com.community.batch.core.BatchRequestQueue;
import com.community.batch.core.BatchRunRecorder;
import com.community.batch.dto.request.BatchRunRequestBody;
import com.community.batch.dto.response.BatchJobView;
import com.community.batch.dto.response.BatchRunRequestView;
import com.community.batch.entity.BatchJobEntity;
import com.community.batch.entity.BatchRequestKind;
import com.community.batch.entity.BatchRunEntity;
import com.community.batch.exception.BatchNoFailuresException;
import com.community.batch.exception.BatchNotFoundException;
import com.community.batch.exception.BatchNotResumableException;
import com.community.batch.exception.BatchRunningException;
import com.community.batch.repository.master.BatchJobRepository;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배치 켜기·끄기, cron 수정, 지금 실행·다시 실행 요청 (docs/features/roles-contract.md 「배치 인프라 결과」, docs/features/batch-contract.md 「0-2 보강」). 입구의 권한은 컨트롤러가
 * 선언한다 — 켜기·끄기·cron 은 {@code batch.manage}, 지금 실행·다시 실행은 {@code batch.run}(민감 조치). Java 배치와 DB 배치(저장 프로시저 + pg_cron)가 같은 길로 들어와
 * {@code source} 로 갈린다.
 *
 * <ul>
 *   <li>설정을 바꾸면 감사 기록({@code BATCH_TOGGLE} · {@code BATCH_SCHEDULE_CHANGE})을 남긴다. Java 배치는 커밋 뒤 이 서버의 예약을 곧바로 맞추고(다른 서버는 주기적으로 따라온다),
 *       DB 배치는 {@code cron.alter_job} 으로 pg_cron 에 바로 반영한다(DB 가 실행 주체라 서버를 몇 대 띄웠든 한 곳의 설정이다).</li>
 *   <li>지금 실행·「실패 지점부터 다시」·「실패 건만 다시」는 <b>요청 표에 줄만 넣고</b> 바로 돌아온다(202 — 결정 7). 배치를 켠 서버({@code roboyam.batch.enabled})가 집어 실행한다.
 *       꺼져 있어도 돈다. 같은 배치가 실행 중이거나 끝나지 않은 요청이 있으면 409 {@code BATCH_RUNNING}(끝나지 않은 요청은 부분 유일 색인으로도 막는다). 파라미터·단계는 넣을 때
 *       검사한다(400 {@code fields.params.<이름>}·{@code fields.steps}). 요청을 넣으면 감사 기록({@code BATCH_RUN} — 종류·요청 번호)을 남긴다.</li>
 *   <li>DB 배치를 쓸 수 없으면(pg_cron 없음·미등록) 켜기/끄기·cron·지금 실행은 409 {@code BATCH_UNAVAILABLE}. DB 배치는 다시 실행이 없다(409 {@code BATCH_NOT_RESUMABLE}·{@code BATCH_NO_FAILURES}).</li>
 * </ul>
 */
@Service
public class BatchAdminService {

    private static final Logger log = LoggerFactory.getLogger(BatchAdminService.class);

    private final BatchJobRepository jobs;
    private final BatchCronPolicy cronPolicy;
    private final BatchQueryService queries;
    private final DbBatchGateway database;
    private final BatchRegistry registry;
    private final BatchRequestQueue requests;
    private final BatchRunRecorder recorder;
    private final AuditLogService audit;
    private final ApplicationEventPublisher events;

    public BatchAdminService(BatchJobRepository jobs, BatchCronPolicy cronPolicy, BatchQueryService queries, DbBatchGateway database, BatchRegistry registry,
                             BatchRequestQueue requests, BatchRunRecorder recorder, AuditLogService audit, ApplicationEventPublisher events) {
        this.jobs = jobs;
        this.cronPolicy = cronPolicy;
        this.queries = queries;
        this.database = database;
        this.registry = registry;
        this.requests = requests;
        this.recorder = recorder;
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

    /**
     * 지금 실행(파라미터 지정 실행 포함)을 요청한다 — 요청 표에 넣고 그 요청을 돌려준다. 순서: 없는 배치 404 → 쓸 수 없는 DB 배치 409 → 실행 중·끝나지 않은 요청 409 → 파라미터·단계 400.
     */
    @Transactional
    public BatchRunRequestView requestRun(AdminActor actor, String jobKey, BatchRunRequestBody body) {
        BatchCatalog definition = definitionOf(jobKey);
        if (!definition.isJava()) {
            queries.usableDbJob(definition);
        }
        ensureIdle(definition.key());
        BatchRunRequestBody request = body == null ? BatchRunRequestBody.empty() : body;
        BatchParameterValidator.Validated validated = BatchParameterValidator.validate(registry.jobOf(definition), request.params(), request.steps());
        return enqueue(actor, definition, BatchRequestKind.NOW, validated.parameters().asMap(), validated.steps(), null);
    }

    /** 「실패 지점부터 다시」를 요청한다 — 원래 실행의 파라미터로. 할 수 없으면 409 {@code BATCH_NOT_RESUMABLE}, 실행 중이면 409 {@code BATCH_RUNNING} */
    @Transactional
    public BatchRunRequestView requestResume(AdminActor actor, String logId) {
        BatchRunEntity run = queries.run(logId);
        BatchCatalog definition = definitionOf(run.getJobKey());
        if (!queries.canResume(run)) {
            throw new BatchNotResumableException();
        }
        ensureIdle(definition.key());
        return enqueue(actor, definition, BatchRequestKind.RESUME, BatchMessageCodec.decodeParams(run.getParams()), null, run.getId());
    }

    /** 「실패 건만 다시」를 요청한다. 다시 할 미해결 실패가 없으면 409 {@code BATCH_NO_FAILURES}, 실행 중이면 409 {@code BATCH_RUNNING} */
    @Transactional
    public BatchRunRequestView requestRetryFailures(AdminActor actor, String logId) {
        BatchRunEntity run = queries.run(logId);
        BatchCatalog definition = definitionOf(run.getJobKey());
        if (!queries.canRetryFailures(run, queries.unresolvedFailures(run.getId()))) {
            throw new BatchNoFailuresException();
        }
        ensureIdle(definition.key());
        return enqueue(actor, definition, BatchRequestKind.RETRY_FAILED, BatchMessageCodec.decodeParams(run.getParams()), null, run.getId());
    }

    /** 같은 배치가 지금 실행 중이거나 끝나지 않은 요청이 있으면 409 */
    private void ensureIdle(String jobKey) {
        if (recorder.isRunning(jobKey) || requests.hasOpen(jobKey)) {
            throw new BatchRunningException();
        }
    }

    private BatchRunRequestView enqueue(AdminActor actor, BatchCatalog definition, BatchRequestKind kind, Map<String, String> params, List<String> steps,
                                       Long sourceRunId) {
        // 앞의 검사와 이 줄 사이에 다른 요청이 먼저 들어오면 부분 유일 색인이 막는다 — 같은 409
        long requestId = requests.enqueue(definition.key(), kind, params, steps, sourceRunId, actor.operatorId()).orElseThrow(BatchRunningException::new);
        AuditEvent event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.BATCH_RUN, AuditResult.SUCCESS).target("BATCH", definition.key())
                .with("kind", kind.name()).with("requestId", String.valueOf(requestId));
        if (sourceRunId != null) {
            event = event.with("sourceRunId", String.valueOf(sourceRunId));
        }
        if (steps != null) {
            event = event.with("steps", String.join(",", steps));
        }
        audit.record(event);
        log.info("배치 실행을 요청했다 jobKey={} kind={} requestId={} operatorId={}", definition.key(), kind, requestId, actor.operatorId());
        return queries.request(requestId);
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

    // ── 감사 기록 ────────────────────────────────────────────────────────────

    private void recordToggle(AdminActor actor, BatchCatalog definition, boolean was, boolean now) {
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.BATCH_TOGGLE, AuditResult.SUCCESS).target("BATCH", definition.key())
                .with("from", was).with("to", now));
    }

    private void recordScheduleChange(AdminActor actor, BatchCatalog definition, String was, String now) {
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.BATCH_SCHEDULE_CHANGE, AuditResult.SUCCESS).target("BATCH", definition.key())
                .with("from", was).with("to", now));
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
