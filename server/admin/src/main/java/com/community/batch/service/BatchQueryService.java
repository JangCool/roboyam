package com.community.batch.service;

import com.community.batch.config.BatchProperties;
import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchMessageCodec;
import com.community.batch.core.BatchParamDefinition;
import com.community.batch.core.BatchRegistry;
import com.community.batch.core.BatchStepDefinition;
import com.community.batch.dto.BatchRunCriteria;
import com.community.batch.dto.BatchRunRequestRow;
import com.community.batch.dto.BatchRunRow;
import com.community.batch.dto.request.BatchFailureSearchRequest;
import com.community.batch.dto.request.BatchLogSearchRequest;
import com.community.batch.dto.response.BatchFailurePage;
import com.community.batch.dto.response.BatchJobView;
import com.community.batch.dto.response.BatchLogPage;
import com.community.batch.dto.response.BatchLogView;
import com.community.batch.dto.response.BatchParamDefView;
import com.community.batch.dto.response.BatchRunDetailView;
import com.community.batch.dto.response.BatchRunFailureView;
import com.community.batch.dto.response.BatchRunRequestView;
import com.community.batch.dto.response.BatchRunStepView;
import com.community.batch.dto.response.BatchStepDefView;
import com.community.batch.entity.BatchJobEntity;
import com.community.batch.entity.BatchRunEntity;
import com.community.batch.entity.BatchRunFailureEntity;
import com.community.batch.entity.BatchRunMode;
import com.community.batch.entity.BatchRunRequestEntity;
import com.community.batch.entity.BatchRunStatus;
import com.community.batch.entity.BatchRunStepEntity;
import com.community.batch.entity.BatchSource;
import com.community.batch.entity.BatchTriggerType;
import com.community.batch.exception.BatchLogNotFoundException;
import com.community.batch.exception.BatchRequestNotFoundException;
import com.community.batch.exception.BatchUnavailableException;
import com.community.batch.repository.master.BatchJobRepository;
import com.community.batch.repository.master.BatchRunDetailRepositoryQDSL;
import com.community.batch.repository.master.BatchRunRepositoryQDSL;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배치 목록·실행 로그·실행 상세·건별 실패·실행 요청 조회(읽기 전용). 이름·설명·결과 요약·단계·파라미터·대상 종류의 이름은 요청 언어로 풀어 준다. 목록은 카탈로그 순서이고, 로그는 시작 시각 최근순이다.
 * 조건이 틀리면 칸 이름과 함께 400 {@code VALIDATION} 으로 알린다(틀린 값은 문구에 싣지 않는다). 쪽 크기는 1~{@value #MAX_SIZE}, 기본 {@value #DEFAULT_SIZE}.
 *
 * <p>단계·파라미터 이름 문구: {@code batch.job.<키>.step.<단계>} → 없으면 공통 {@code batch.step.<단계>} → 없으면 단계 키. 파라미터 {@code batch.job.<키>.param.<이름>}(안내
 * {@code .help}, 없으면 null). 대상 종류 {@code batch.target.<코드>} → 없으면 코드.
 */
@Service
@Transactional(readOnly = true)
public class BatchQueryService {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private static final Pattern JOB_KEY = Pattern.compile("[a-z][a-z0-9-]{0,39}");

    private final BatchJobRepository jobs;
    private final BatchRunRepositoryQDSL runQueries;
    private final BatchRunDetailRepositoryQDSL details;
    private final BatchCronPolicy cronPolicy;
    private final BatchProperties properties;
    private final Messages messages;
    private final DbBatchGateway database;
    private final BatchRegistry registry;

    public BatchQueryService(BatchJobRepository jobs, BatchRunRepositoryQDSL runQueries, BatchRunDetailRepositoryQDSL details, BatchCronPolicy cronPolicy,
                             BatchProperties properties, Messages messages, DbBatchGateway database, BatchRegistry registry) {
        this.jobs = jobs;
        this.runQueries = runQueries;
        this.details = details;
        this.cronPolicy = cronPolicy;
        this.properties = properties;
        this.messages = messages;
        this.database = database;
        this.registry = registry;
    }

    /** 카탈로그의 모든 배치(정의 + 설정 + 마지막 실행 요약 + 끝나지 않은 요청 + 파라미터·단계) — Java 배치와 DB 배치가 한 목록이다 */
    public List<BatchJobView> list() {
        Map<String, BatchJobEntity> settings = new HashMap<>();
        jobs.findAll().forEach(job -> settings.put(job.getJobKey(), job));
        ZonedDateTime now = ZonedDateTime.now(properties.zoneId());
        List<String> keys = Arrays.stream(BatchCatalog.values()).map(BatchCatalog::key).toList();
        Map<String, BatchRunEntity> latest = runQueries.latestByJobKey(keys);
        Map<String, BatchRunRequestRow> open = details.openByJobKey(keys);
        DbSnapshot db = dbSnapshot(latest);
        return Arrays.stream(BatchCatalog.values())
                .map(definition -> definition.isJava() ? view(definition, settings.get(definition.key()), now, latest.get(definition.key()), open.get(definition.key()))
                        : dbView(definition, db, now, open.get(definition.key())))
                .toList();
    }

    /**
     * DB 배치를 쓸 수 있으면 그 DB 쪽 정보(pg_cron 의 job)를, 아니면 {@link BatchUnavailableException} 을 던진다 — 켜기/끄기·cron 수정·지금 실행이 먼저 부른다.
     * 카탈로그에서 DB 배치가 아닌 것을 넘기면 안 된다.
     */
    public DbBatchGateway.DbJob usableDbJob(BatchCatalog definition) {
        DbSnapshot db = dbSnapshot(Map.of());
        DbBatchGateway.DbJob job = db.jobs().get(definition.key());
        if (unavailableCode(db.cron(), job) != null) {
            throw new BatchUnavailableException();
        }
        return job;
    }

    /** pg_cron 의 상태와 DB 에 정의된 배치 */
    private DbSnapshot dbSnapshot(Map<String, BatchRunEntity> latest) {
        DbBatchGateway.PgCronState cron = database.state();
        Map<String, DbBatchGateway.DbJob> dbJobs = database.jobs(cron.ready());
        return new DbSnapshot(cron, dbJobs, latest);
    }

    private record DbSnapshot(DbBatchGateway.PgCronState cron, Map<String, DbBatchGateway.DbJob> jobs, Map<String, BatchRunEntity> last) {
    }

    /** 쓸 수 없는 이유 코드. 쓸 수 있으면 null — pg_cron 을 쓸 수 없거나, DB 에 정의가 없거나, pg_cron 에 등록되어 있지 않으면 쓸 수 없다 */
    private static String unavailableCode(DbBatchGateway.PgCronState cron, DbBatchGateway.DbJob job) {
        if (!cron.ready()) {
            return cron.status();
        }
        return job == null || !job.scheduled() ? "NOT_REGISTERED" : null;
    }

    private BatchJobView dbView(BatchCatalog definition, DbSnapshot db, ZonedDateTime now, BatchRunRequestRow open) {
        DbBatchGateway.DbJob job = db.jobs().get(definition.key());
        String code = unavailableCode(db.cron(), job);
        boolean available = code == null;
        String defaultCron = job == null ? "" : job.defaultCron();
        String schedule = available ? job.schedule() : defaultCron;
        boolean enabled = available && job.active();
        ZonedDateTime next = null;
        if (enabled) {
            ZonedDateTime inCronZone = now.withZoneSameInstant(cronZone(db.cron().timezone()));
            next = cronPolicy.nextPgCron(schedule, inCronZone);
        }
        BatchRunEntity last = db.last().get(definition.key());
        String lastStatus = last == null ? null : last.getStatus().name();
        return new BatchJobView(definition.key(), messages.get(new MessageRef(definition.nameKey())), messages.get(new MessageRef(definition.descriptionKey())), null, null,
                definition.category().name(), BatchSource.POSTGRESQL.name(), schedule, defaultCron, enabled, next == null ? null : next.toInstant(),
                last == null ? null : last.getStartedAt(), lastStatus, last == null ? null : last.getAffectedCount(), last == null ? null : listMessage(last),
                BatchRunStatus.RUNNING.name().equals(lastStatus), available, available, code == null ? null : messages.get(new MessageRef("batch.unavailable." + code)),
                db.cron().timezone(), last == null ? null : String.valueOf(last.getId()), open == null ? null : requestView(open), paramViews(definition),
                stepViews(definition), false);
    }

    /** pg_cron 의 시간대 이름을 자바 시간대로 — 모르는 이름이면 GMT 로 본다 */
    private static ZoneId cronZone(String name) {
        try {
            return ZoneId.of(name);
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }

    public BatchLogPage logs(BatchLogSearchRequest request) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        MessageRef invalid = new MessageRef("batch.query.invalid");

        String jobKey = blankToNull(request.jobName());
        if (jobKey != null && !JOB_KEY.matcher(jobKey).matches()) {
            fields.put("jobName", invalid);
        }
        BatchRunStatus status = enumOf("status", request.status(), BatchRunStatus::valueOf, fields, invalid);
        BatchTriggerType trigger = enumOf("trigger", request.trigger(), BatchTriggerType::valueOf, fields, invalid);
        Instant from = instant("from", request.from(), fields, invalid);
        Instant to = instant("to", request.to(), fields, invalid);
        if (from != null && to != null && from.isAfter(to)) {
            fields.put("to", new MessageRef("batch.period.reversed"));
        }
        MessageRef pageRange = new MessageRef("batch.page.range", String.valueOf(MAX_SIZE));
        int page = integer("page", request.page(), 0, 0, Integer.MAX_VALUE / MAX_SIZE, fields, pageRange);
        int size = integer("size", request.size(), DEFAULT_SIZE, 1, MAX_SIZE, fields, pageRange);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }

        BatchRunCriteria criteria = new BatchRunCriteria(jobKey, status, trigger, from, to, page, size);
        List<BatchLogView> items = runQueries.search(criteria).stream().map(this::logView).toList();
        return new BatchLogPage(items, runQueries.count(criteria), page, size);
    }

    /** 실행 상세 — 단계·건수·파라미터·이어 받기·다시 실행 가능 여부. 없으면 404 */
    public BatchRunDetailView detail(String logId) {
        BatchRunRow row = runQueries.findRow(parseId(logId)).orElseThrow(BatchLogNotFoundException::new);
        BatchRunEntity run = row.run();
        boolean java = run.getSource() == BatchSource.JAVA;
        MessageRef summary = BatchMessageCodec.decode(run.getMessageKey(), run.getMessageArgs());
        List<BatchRunStepEntity> stepRows = java ? details.steps(run.getId()) : List.of();
        List<BatchRunStepView> steps = java ? stepRows.stream().map(step -> stepView(run.getJobKey(), step)).toList() : List.of(databaseStep(run));

        Long read = run.getReadCount();
        Long processed = run.getAffectedCount();
        Long skipped = run.getSkippedCount();
        Long failed = run.getFailedCount();
        if (java && run.getStatus() == BatchRunStatus.RUNNING && !stepRows.isEmpty()) {
            // 실행 중에는 실행 줄의 건수가 비어 있다 — 덩어리마다 고쳐지는 단계 건수로 보인다(확정 규칙 1)
            BatchRunStepEntity main = mainStepRow(run.getJobKey(), stepRows);
            boolean retry = run.getRunMode() == BatchRunMode.RETRY_FAILED;
            read = retry ? stepRows.stream().mapToLong(BatchRunStepEntity::getReadCount).sum() : main.getReadCount();
            processed = retry ? stepRows.stream().mapToLong(BatchRunStepEntity::getProcessedCount).sum() : main.getProcessedCount();
            skipped = retry ? stepRows.stream().mapToLong(BatchRunStepEntity::getSkippedCount).sum() : main.getSkippedCount();
            failed = stepRows.stream().mapToLong(BatchRunStepEntity::getFailedCount).sum();
        }
        long unresolved = java ? details.countUnresolved(run.getId()) : 0;
        String nodeId = run.getNodeId() == null ? null : String.valueOf(run.getNodeId());
        return new BatchRunDetailView(String.valueOf(run.getId()), run.getJobKey(), nameOf(run.getJobKey()), run.getSource().name(), run.getTriggerType().name(),
                row.operatorName(), run.getStartedAt(), run.getFinishedAt(), run.getStatus().name(), processed, listMessage(run), runModeOf(run), failed,
                run.getScheduledAt(), summary == null ? null : messages.get(summary), run.getErrorMessage(), nodeId,
                BatchMessageCodec.decodeParams(run.getParams()), read, processed, skipped, run.getCheckpoint(),
                run.getResumedFromRunId() == null ? null : String.valueOf(run.getResumedFromRunId()),
                details.resumedBy(run.getId()).stream().map(String::valueOf).toList(), run.getRequestId() == null ? null : String.valueOf(run.getRequestId()),
                steps, unresolved, canResume(run), canRetryFailures(run, unresolved),
                details.openBySourceRun(run.getId()).map(this::requestView).orElse(null));
    }

    /** 「실패 지점부터 다시」를 할 수 있나 — 실패로 끝났고 체크포인트가 있고 Java 작업이 지원하며, 아직 이어 받은 실행이 없을 때(확정 규칙 3) */
    public boolean canResume(BatchRunEntity run) {
        if (run.getSource() != BatchSource.JAVA || run.getStatus() != BatchRunStatus.FAIL || run.getCheckpoint() == null) {
            return false;
        }
        BatchCatalog definition = BatchCatalog.ofKey(run.getJobKey());
        return definition != null && registry.resumable(definition) && !details.hasResume(run.getId());
    }

    /** 「실패 건만 다시」를 할 수 있나 — 미해결 실패가 있고 Java 작업일 때 */
    public boolean canRetryFailures(BatchRunEntity run, long unresolved) {
        return unresolved > 0 && run.getSource() == BatchSource.JAVA && registry.jobOf(run.getJobKey()) != null;
    }

    /** 실행 줄 하나(다시 실행 요청이 원래 실행을 볼 때). 없으면 404 */
    public BatchRunEntity run(String logId) {
        return runQueries.findRow(parseId(logId)).map(BatchRunRow::run).orElseThrow(BatchLogNotFoundException::new);
    }

    /** 아직 해결하지 않은 실패 수 */
    public long unresolvedFailures(long runId) {
        return details.countUnresolved(runId);
    }

    /** 실행의 건별 실패 한 쪽 — 일어난 시각 최근순 */
    public BatchFailurePage failures(String logId, BatchFailureSearchRequest request) {
        BatchRunEntity run = run(logId);
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        MessageRef invalid = new MessageRef("batch.query.invalid");
        String resolvedRaw = blankToNull(request.resolved());
        Boolean resolved = null;
        if (resolvedRaw != null) {
            if ("true".equals(resolvedRaw) || "false".equals(resolvedRaw)) {
                resolved = Boolean.valueOf(resolvedRaw);
            } else {
                fields.put("resolved", invalid);
            }
        }
        MessageRef pageRange = new MessageRef("batch.page.range", String.valueOf(MAX_SIZE));
        int page = integer("page", request.page(), 0, 0, Integer.MAX_VALUE / MAX_SIZE, fields, pageRange);
        int size = integer("size", request.size(), DEFAULT_SIZE, 1, MAX_SIZE, fields, pageRange);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        List<BatchRunFailureView> items = details.failures(run.getId(), resolved, page, size).stream().map(failure -> failureView(run.getJobKey(), failure)).toList();
        return new BatchFailurePage(items, details.countFailures(run.getId(), resolved), page, size);
    }

    /** 실행 요청 한 줄. 없으면 404 */
    public BatchRunRequestView request(String requestId) {
        return details.request(parseRequestId(requestId)).map(this::requestView).orElseThrow(BatchRequestNotFoundException::new);
    }

    public BatchRunRequestView request(long requestId) {
        return details.request(requestId).map(this::requestView).orElseThrow(BatchRequestNotFoundException::new);
    }

    private BatchRunRequestView requestView(BatchRunRequestRow row) {
        BatchRunRequestEntity request = row.request();
        MessageRef message = BatchMessageCodec.decode(request.getMessageKey(), request.getMessageArgs());
        return new BatchRunRequestView(String.valueOf(request.getId()), request.getJobKey(), request.getKind().name(), request.getStatus().name(),
                request.getRunId() == null ? null : String.valueOf(request.getRunId()), BatchMessageCodec.decodeParams(request.getParams()),
                BatchMessageCodec.decodeSteps(request.getSteps()), request.getRequestedAt(), request.getStartedAt(), request.getFinishedAt(),
                message == null ? null : messages.get(message), row.operatorName());
    }

    private BatchJobView view(BatchCatalog definition, BatchJobEntity setting, ZonedDateTime now, BatchRunEntity latest, BatchRunRequestRow open) {
        // 설정 행은 서버가 뜰 때 카탈로그와 맞춰지므로 늘 있다. 없으면(맞추기 전) 카탈로그 기본값으로 보여 준다
        boolean enabled = setting == null ? definition.defaultEnabled() : setting.isEnabled();
        String cron = setting == null ? definition.defaultCron() : setting.getCron();
        ZonedDateTime next = enabled ? cronPolicy.next(cron, now) : null;
        String lastStatus = setting == null ? null : setting.getLastStatus();
        return new BatchJobView(definition.key(), messages.get(new MessageRef(definition.nameKey())), messages.get(new MessageRef(definition.descriptionKey())), null, null,
                definition.category().name(), BatchSource.JAVA.name(), cron, definition.defaultCron(), enabled, next == null ? null : next.toInstant(),
                setting == null ? null : setting.getLastRunAt(), lastStatus, setting == null ? null : setting.getLastAffectedCount(),
                setting == null ? null : lastMessage(setting), BatchRunStatus.RUNNING.name().equals(lastStatus), true, true, null, properties.zone(),
                latest == null ? null : String.valueOf(latest.getId()), open == null ? null : requestView(open), paramViews(definition), stepViews(definition),
                registry.resumable(definition));
    }

    private List<BatchParamDefView> paramViews(BatchCatalog definition) {
        return registry.paramsOf(definition).stream().map(param -> paramView(definition.key(), param)).toList();
    }

    private BatchParamDefView paramView(String jobKey, BatchParamDefinition param) {
        String base = "batch.job." + jobKey + ".param." + param.name();
        String label = messages.find(new MessageRef(base));
        return new BatchParamDefView(param.name(), label == null ? param.name() : label, param.type().name(), param.required(), messages.find(new MessageRef(base + ".help")));
    }

    private List<BatchStepDefView> stepViews(BatchCatalog definition) {
        return registry.stepsOf(definition).stream().map(step -> new BatchStepDefView(step.name(), stepLabel(definition.key(), step.name()), step.selectable())).toList();
    }

    private String stepLabel(String jobKey, String step) {
        String label = messages.find(new MessageRef("batch.job." + jobKey + ".step." + step));
        if (label == null) {
            label = messages.find(new MessageRef("batch.step." + step));
        }
        return label == null ? step : label;
    }

    private BatchRunStepView stepView(String jobKey, BatchRunStepEntity step) {
        return new BatchRunStepView(step.getStepName(), stepLabel(jobKey, step.getStepName()), step.getStepOrder(), step.getStatus().name(), step.getReadCount(),
                step.getProcessedCount(), step.getFailedCount(), step.getSkippedCount(), step.getStartedAt(), step.getFinishedAt(), step.getCheckpoint());
    }

    /** DB 배치는 단계 기록이 없다 — 실행 줄로 한 단계를 만들어 보인다(처리 건수만) */
    private BatchRunStepView databaseStep(BatchRunEntity run) {
        return new BatchRunStepView(BatchStepDefinition.MAIN, stepLabel(run.getJobKey(), BatchStepDefinition.MAIN), 1, run.getStatus().name(), null,
                run.getAffectedCount(), null, null, run.getStartedAt(), run.getFinishedAt(), null);
    }

    private BatchRunStepEntity mainStepRow(String jobKey, List<BatchRunStepEntity> steps) {
        BatchCatalog definition = BatchCatalog.ofKey(jobKey);
        String main = definition == null ? null : registry.mainStepOf(definition);
        return steps.stream().filter(step -> step.getStepName().equals(main)).findFirst().orElse(steps.get(0));
    }

    private BatchRunFailureView failureView(String jobKey, BatchRunFailureEntity failure) {
        String target = messages.find(new MessageRef("batch.target." + failure.getTargetType()));
        return new BatchRunFailureView(String.valueOf(failure.getId()), failure.getStepName(), stepLabel(jobKey, failure.getStepName()), failure.getTargetType(),
                target == null ? failure.getTargetType() : target, failure.getTargetId(), failure.getErrorCode(), failure.getMessage(), failure.getAttempts(),
                failure.isResolved(), failure.getResolvedByRunId() == null ? null : String.valueOf(failure.getResolvedByRunId()), failure.getOccurredAt());
    }

    /** 마지막 실행의 한 줄 — 실패 사유가 있으면 사유, 없으면 결과 요약 */
    private String lastMessage(BatchJobEntity setting) {
        if (setting.getLastError() != null) {
            return setting.getLastError();
        }
        MessageRef summary = BatchMessageCodec.decode(setting.getLastMessageKey(), setting.getLastMessageArgs());
        return summary == null ? null : messages.get(summary);
    }

    private BatchLogView logView(BatchRunRow row) {
        BatchRunEntity run = row.run();
        return new BatchLogView(String.valueOf(run.getId()), run.getJobKey(), nameOf(run.getJobKey()), run.getSource().name(), run.getTriggerType().name(), row.operatorName(),
                run.getStartedAt(), run.getFinishedAt(), run.getStatus().name(), run.getAffectedCount(), listMessage(run), runModeOf(run), run.getFailedCount());
    }

    private static String runModeOf(BatchRunEntity run) {
        return run.getRunMode() == null ? BatchRunMode.NORMAL.name() : run.getRunMode().name();
    }

    private String listMessage(BatchRunEntity run) {
        if (run.getErrorMessage() != null) {
            return run.getErrorMessage();
        }
        MessageRef summary = BatchMessageCodec.decode(run.getMessageKey(), run.getMessageArgs());
        return summary == null ? null : messages.get(summary);
    }

    /** 배치 이름 — 카탈로그에서 사라진 배치의 로그는 키를 그대로 보인다 */
    private String nameOf(String jobKey) {
        BatchCatalog definition = BatchCatalog.ofKey(jobKey);
        return definition == null ? jobKey : messages.get(new MessageRef(definition.nameKey()));
    }

    private static long parseId(String logId) {
        try {
            return Long.parseLong(logId);
        } catch (NumberFormatException e) {
            throw new BatchLogNotFoundException();
        }
    }

    private static long parseRequestId(String requestId) {
        try {
            return Long.parseLong(requestId);
        } catch (NumberFormatException e) {
            throw new BatchRequestNotFoundException();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static <E> E enumOf(String field, String raw, Function<String, E> parse, Map<String, MessageRef> fields, MessageRef invalid) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return parse.apply(value);
        } catch (IllegalArgumentException e) {
            fields.put(field, invalid);
            return null;
        }
    }

    private static Instant instant(String field, String raw, Map<String, MessageRef> fields, MessageRef invalid) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            fields.put(field, invalid);
            return null;
        }
    }

    private static int integer(String field, String raw, int fallback, int min, int max, Map<String, MessageRef> fields, MessageRef invalid) {
        String value = blankToNull(raw);
        if (value == null) {
            return fallback;
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed < min || parsed > max) {
                fields.put(field, invalid);
                return fallback;
            }
            return (int) parsed;
        } catch (NumberFormatException e) {
            fields.put(field, invalid);
            return fallback;
        }
    }
}
