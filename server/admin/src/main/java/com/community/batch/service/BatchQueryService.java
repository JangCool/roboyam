package com.community.batch.service;

import com.community.batch.catalog.BatchCatalog;
import com.community.batch.config.BatchProperties;
import com.community.batch.dto.BatchRunCriteria;
import com.community.batch.dto.BatchRunRow;
import com.community.batch.dto.request.BatchLogSearchRequest;
import com.community.batch.dto.response.BatchJobView;
import com.community.batch.dto.response.BatchLogDetailView;
import com.community.batch.dto.response.BatchLogPage;
import com.community.batch.dto.response.BatchLogView;
import com.community.batch.entity.BatchJobEntity;
import com.community.batch.entity.BatchRunEntity;
import com.community.batch.entity.BatchRunStatus;
import com.community.batch.entity.BatchSource;
import com.community.batch.entity.BatchTriggerType;
import com.community.batch.exception.BatchLogNotFoundException;
import com.community.batch.exception.BatchUnavailableException;
import com.community.batch.repository.master.BatchJobRepository;
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
 * 배치 목록·실행 로그 조회(읽기 전용). 이름·설명·결과 요약은 요청 언어로 풀어 준다. 목록은 카탈로그 순서이고, 로그는 시작 시각 최근순이다.
 * 로그 조건이 틀리면 칸 이름과 함께 400 {@code VALIDATION} 으로 알린다(틀린 값은 문구에 싣지 않는다). 쪽 크기는 1~{@value #MAX_SIZE}, 기본 {@value #DEFAULT_SIZE}.
 */
@Service
@Transactional(readOnly = true)
public class BatchQueryService {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private static final Pattern JOB_KEY = Pattern.compile("[a-z][a-z0-9-]{0,39}");

    private final BatchJobRepository jobs;
    private final BatchRunRepositoryQDSL runQueries;
    private final BatchCronPolicy cronPolicy;
    private final BatchProperties properties;
    private final Messages messages;
    private final DbBatchGateway database;

    public BatchQueryService(BatchJobRepository jobs, BatchRunRepositoryQDSL runQueries, BatchCronPolicy cronPolicy, BatchProperties properties, Messages messages,
                             DbBatchGateway database) {
        this.jobs = jobs;
        this.runQueries = runQueries;
        this.cronPolicy = cronPolicy;
        this.properties = properties;
        this.messages = messages;
        this.database = database;
    }

    /** 카탈로그의 모든 배치(정의 + 설정 + 마지막 실행 요약) — Java 배치와 DB 배치가 한 목록이다 */
    public List<BatchJobView> list() {
        Map<String, BatchJobEntity> settings = new HashMap<>();
        jobs.findAll().forEach(job -> settings.put(job.getJobKey(), job));
        ZonedDateTime now = ZonedDateTime.now(properties.zoneId());
        DbSnapshot db = dbSnapshot();
        return Arrays.stream(BatchCatalog.values())
                .map(definition -> definition.isJava() ? view(definition, settings.get(definition.key()), now) : dbView(definition, db, now)).toList();
    }

    /**
     * DB 배치를 쓸 수 있으면 그 DB 쪽 정보(pg_cron 의 job)를, 아니면 {@link BatchUnavailableException} 을 던진다 — 켜기/끄기·cron 수정·지금 실행이 먼저 부른다.
     * 카탈로그에서 DB 배치가 아닌 것을 넘기면 안 된다.
     */
    public DbBatchGateway.DbJob usableDbJob(BatchCatalog definition) {
        DbSnapshot db = dbSnapshot();
        DbBatchGateway.DbJob job = db.jobs().get(definition.key());
        if (unavailableCode(db.cron(), job) != null) {
            throw new BatchUnavailableException();
        }
        return job;
    }

    /** 실행 로그 한 줄의 상태 — DB 배치를 「지금 실행」한 직후 감사 기록에 결과를 싣는 데 쓴다 */
    public BatchRunStatus runStatus(long runId) {
        return runQueries.findRow(runId).map(row -> row.run().getStatus()).orElseThrow(BatchLogNotFoundException::new);
    }

    /** pg_cron 의 상태와 DB 에 정의된 배치, 가장 최근 실행 — 한 번에 읽는다 */
    private DbSnapshot dbSnapshot() {
        DbBatchGateway.PgCronState cron = database.state();
        Map<String, DbBatchGateway.DbJob> dbJobs = database.jobs(cron.ready());
        Map<String, BatchRunEntity> last = runQueries.latestByJobKey(BatchCatalog.databaseJobs().stream().map(BatchCatalog::key).toList());
        return new DbSnapshot(cron, dbJobs, last);
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

    private BatchJobView dbView(BatchCatalog definition, DbSnapshot db, ZonedDateTime now) {
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
                db.cron().timezone());
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

    public BatchLogDetailView logDetail(String logId) {
        long id;
        try {
            id = Long.parseLong(logId);
        } catch (NumberFormatException e) {
            throw new BatchLogNotFoundException();
        }
        BatchRunRow row = runQueries.findRow(id).orElseThrow(BatchLogNotFoundException::new);
        BatchRunEntity run = row.run();
        MessageRef summary = BatchMessageCodec.decode(run.getMessageKey(), run.getMessageArgs());
        return new BatchLogDetailView(String.valueOf(run.getId()), run.getJobKey(), nameOf(run.getJobKey()), run.getSource().name(), run.getTriggerType().name(), row.operatorName(),
                run.getScheduledAt(), run.getStartedAt(), run.getFinishedAt(), run.getStatus().name(), run.getAffectedCount(), listMessage(run),
                summary == null ? null : messages.get(summary), run.getErrorMessage(), run.getNodeId());
    }

    private BatchJobView view(BatchCatalog definition, BatchJobEntity setting, ZonedDateTime now) {
        // 설정 행은 서버가 뜰 때 카탈로그와 맞춰지므로 늘 있다. 없으면(맞추기 전) 카탈로그 기본값으로 보여 준다
        boolean enabled = setting == null ? definition.defaultEnabled() : setting.isEnabled();
        String cron = setting == null ? definition.defaultCron() : setting.getCron();
        ZonedDateTime next = enabled ? cronPolicy.next(cron, now) : null;
        String lastStatus = setting == null ? null : setting.getLastStatus();
        return new BatchJobView(definition.key(), messages.get(new MessageRef(definition.nameKey())), messages.get(new MessageRef(definition.descriptionKey())), null, null,
                definition.category().name(), BatchSource.JAVA.name(), cron, definition.defaultCron(), enabled, next == null ? null : next.toInstant(),
                setting == null ? null : setting.getLastRunAt(), lastStatus, setting == null ? null : setting.getLastAffectedCount(),
                setting == null ? null : lastMessage(setting), BatchRunStatus.RUNNING.name().equals(lastStatus), true, true, null, properties.zone());
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
                run.getStartedAt(), run.getFinishedAt(), run.getStatus().name(), run.getAffectedCount(), listMessage(run));
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
