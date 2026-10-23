package com.community.batch.core;

import static com.community.batch.core.BatchJdbc.ts;

import com.community.batch.entity.BatchRunMode;
import com.community.batch.entity.BatchRunStatus;
import com.community.batch.entity.BatchStepStatus;
import com.community.batch.entity.BatchTriggerType;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 배치 실행 로그({@code batch_runs})·단계({@code batch_run_steps})와 목록의 「마지막 실행 요약」({@code batch_jobs.last_*})을 쓴다. 배치 일 자체와는 <b>별도 트랜잭션</b>이다 —
 * 시작 줄은 배치가 도는 동안에도 다른 곳에서 보이고(실행 중 표시), 배치가 실패해 롤백돼도 「실패했다」는 남는다. 예외는 덩어리 진행({@link #addStepProgress})으로,
 * 덩어리 저장 확정과 <b>같은 트랜잭션</b>에 참여한다(15-3 성능 규칙 — 성공 건은 건별로 기록하지 않고 덩어리마다 단계 건수·체크포인트를 한 번 고친다).
 * Native SQL 인 이유는 {@link BatchJdbc}.
 */
@Component
public class BatchRunRecorder {

    /** 서버가 죽어 끝맺지 못한 실행을 닫을 때 쓰는 요약 문구 키 */
    public static final String INTERRUPTED_KEY = "batch.error.interrupted";

    /** 덩어리 크기 기본값·범위 — 작업별 설정 {@code batch_params(job_key, 'chunk_size')} */
    public static final int DEFAULT_CHUNK_SIZE = 500;

    /** 실패 한도 기본값 — 작업별 설정 {@code batch_params(job_key, 'failure_limit')}. 넘으면 실행을 FAIL 로 멈춘다 */
    public static final int DEFAULT_FAILURE_LIMIT = 100;

    /** 시작한 실행 — 끝낼 때 이 값으로 줄을 찾는다 */
    public record Started(long runId, Instant startedAt) {
    }

    /** 시작할 때 넣는 단계 한 줄 — 상태는 {@code PENDING} 이나 {@code SKIP}(고르지 않음·이미 끝남·다시 할 실패 없음) */
    public record StepRow(String name, int order, BatchStepStatus status) {
    }

    /** 실행 시작 */
    public record StartCommand(String jobKey, BatchTriggerType trigger, Instant scheduledAt, Long operatorId, BatchRunMode mode, String paramsJson,
                               Long resumedFromRunId, Long requestId, List<StepRow> steps) {
    }

    /**
     * 실행 끝 — {@code status} 는 OK · SKIP · FAIL. 실패는 {@code error}(이미 가린 한 줄) 또는 {@code message}(문구 키 — 예: 실패 한도)를, 그 밖은 {@code message} 요약을 싣는다.
     * 건수는 처리({@code affected_count}) · 읽음 · 실패 · 건너뜀.
     */
    public record Finish(BatchRunStatus status, Long processed, Long read, Long failed, Long skipped, String checkpoint, MessageRef message, String error) {
    }

    /** 다시 돌릴 원래 실행 */
    public record SourceRun(long id, String jobKey, BatchRunStatus status, BatchRunMode mode, String paramsJson, String checkpoint, Map<String, SourceStep> steps) {
    }

    public record SourceStep(String name, BatchStepStatus status, String checkpoint) {
    }

    /** 작업별 설정값 */
    public record Settings(int chunkSize, int failureLimit) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate requiresNew;
    private final SnowflakeIdGenerator ids;
    private final int nodeId;

    @Autowired
    public BatchRunRecorder(DataSource dataSource, PlatformTransactionManager transactions, SnowflakeIdGenerator ids, @Value("${roboyam.node-id}") int nodeId) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.requiresNew = BatchJdbc.requiresNew(transactions);
        this.ids = ids;
        this.nodeId = nodeId;
    }

    /**
     * 실행 중 줄과 단계 줄을 넣고, 요청에서 왔으면 요청에 실행 번호를 잇는다. 같은 배치가 이미 실행 중이거나(유일 색인) 같은 예약 시각의 줄이 이미 있거나,
     * 같은 실행을 두 번 이어 받으려 하면(RESUME 유일 색인) {@code DataIntegrityViolationException} 이 난다 — 부르는 쪽이 「건너뜀」·「거절」로 다룬다.
     */
    public Started start(StartCommand command) {
        return requiresNew.execute(status -> {
            Instant startedAt = BatchJdbc.now();
            long runId = ids.nextId();
            jdbc.update("INSERT INTO batch_runs (id, job_key, status, trigger_type, scheduled_at, started_at, operator_id, node_id, source, run_mode, params, "
                            + "resumed_from_run_id, request_id) VALUES (?, ?, 'RUNNING', ?, ?, ?, ?, ?, 'JAVA', ?, ?, ?, ?)",
                    runId, command.jobKey(), command.trigger().name(), ts(command.scheduledAt()), ts(startedAt), command.operatorId(), nodeId, command.mode().name(),
                    command.paramsJson(), command.resumedFromRunId(), command.requestId());
            for (StepRow step : command.steps()) {
                boolean skipped = step.status() == BatchStepStatus.SKIP;
                jdbc.update("INSERT INTO batch_run_steps (run_id, step_name, step_order, status, started_at, finished_at) VALUES (?, ?, ?, ?, ?, ?)",
                        runId, step.name(), step.order(), step.status().name(), skipped ? ts(startedAt) : null, skipped ? ts(startedAt) : null);
            }
            if (command.requestId() != null) {
                jdbc.update("UPDATE batch_run_requests SET run_id = ? WHERE id = ? AND status = 'RUNNING'", runId, command.requestId());
            }
            updateLastRun(command.jobKey(), startedAt, BatchRunStatus.RUNNING, null, null, null);
            return new Started(runId, startedAt);
        });
    }

    /** 실행을 끝낸다 */
    public void finish(Started started, String jobKey, Finish finish) {
        requiresNew.executeWithoutResult(status -> {
            String messageKey = finish.message() == null ? null : finish.message().key();
            String messageArgs = finish.message() == null ? null : BatchMessageCodec.encodeArgs(finish.message());
            int updated = jdbc.update("UPDATE batch_runs SET status = ?, finished_at = ?, affected_count = ?, read_count = ?, failed_count = ?, skipped_count = ?, "
                            + "checkpoint = ?, message_key = ?, message_args = ?, error_message = ? WHERE id = ? AND status = 'RUNNING'",
                    finish.status().name(), ts(BatchJdbc.now()), finish.processed(), finish.read(), finish.failed(), finish.skipped(), finish.checkpoint(), messageKey,
                    messageArgs, finish.error(), started.runId());
            if (updated == 0) {
                throw new IllegalStateException("실행 로그 줄이 없거나 이미 닫혔다: " + started.runId());
            }
            updateLastRun(jobKey, started.startedAt(), finish.status(), finish.processed(), finish.message(), finish.error());
        });
    }

    /** 단계에 들어간다 — {@code RUNNING} + 시작 시각 */
    public void stepRunning(long runId, String step) {
        requiresNew.executeWithoutResult(status -> jdbc.update(
                "UPDATE batch_run_steps SET status = 'RUNNING', started_at = ? WHERE run_id = ? AND step_name = ? AND status = 'PENDING'",
                ts(BatchJdbc.now()), runId, step));
    }

    /** 단계를 끝낸다 — OK · FAIL · SKIP. 이미 끝난 단계는 건드리지 않는다 */
    public void finishStep(long runId, String step, BatchStepStatus result) {
        requiresNew.executeWithoutResult(status -> jdbc.update(
                "UPDATE batch_run_steps SET status = ?, finished_at = ?, started_at = coalesce(started_at, ?) WHERE run_id = ? AND step_name = ? AND status IN ('PENDING', 'RUNNING')",
                result.name(), ts(BatchJdbc.now()), ts(BatchJdbc.now()), runId, step));
    }

    /**
     * 덩어리 하나만큼 단계 건수를 더하고 체크포인트를 옮긴다(null 이면 그대로). <b>부르는 쪽 트랜잭션에 참여한다</b> — 덩어리 처리와 같은 트랜잭션에서 불러 함께 확정·롤백되게 한다.
     */
    public void addStepProgress(long runId, String step, long read, long processed, long failed, long skipped, String checkpoint) {
        jdbc.update("UPDATE batch_run_steps SET read_count = read_count + ?, processed_count = processed_count + ?, failed_count = failed_count + ?, "
                        + "skipped_count = skipped_count + ?, checkpoint = coalesce(?, checkpoint) WHERE run_id = ? AND step_name = ?",
                read, processed, failed, skipped, checkpoint, runId, step);
    }

    /** 작업별 설정값(덩어리 크기·실패 한도) — {@code batch_params} 를 {@code batch_param()} 으로 읽어 범위 밖이면 기본값 */
    public Settings settingsOf(String jobKey) {
        return jdbc.queryForObject("SELECT batch_param(?, 'chunk_size', ?, 1, 10000), batch_param(?, 'failure_limit', ?, 0, 100000)",
                (rs, row) -> new Settings((int) rs.getLong(1), (int) rs.getLong(2)), jobKey, DEFAULT_CHUNK_SIZE, jobKey, DEFAULT_FAILURE_LIMIT);
    }

    /** 예약 실행이 켜져 있나({@code batch_jobs.enabled}). 설정 행이 없으면 꺼진 것으로 본다 */
    public boolean scheduleEnabled(String jobKey) {
        List<Boolean> enabled = jdbc.queryForList("SELECT enabled FROM batch_jobs WHERE job_key = ?", Boolean.class, jobKey);
        return !enabled.isEmpty() && Boolean.TRUE.equals(enabled.get(0));
    }

    /** 다시 돌릴 원래 실행과 그 단계 */
    public Optional<SourceRun> sourceRun(long runId) {
        List<SourceRun> runs = jdbc.query("SELECT id, job_key, status, run_mode, params, checkpoint FROM batch_runs WHERE id = ?",
                (rs, row) -> new SourceRun(rs.getLong("id"), rs.getString("job_key"), BatchRunStatus.valueOf(rs.getString("status")),
                        BatchRunMode.valueOf(rs.getString("run_mode")), rs.getString("params"), rs.getString("checkpoint"), new LinkedHashMap<>()), runId);
        if (runs.isEmpty()) {
            return Optional.empty();
        }
        SourceRun run = runs.get(0);
        jdbc.query("SELECT step_name, status, checkpoint FROM batch_run_steps WHERE run_id = ? ORDER BY step_order", rs -> {
            run.steps().put(rs.getString(1), new SourceStep(rs.getString(1), BatchStepStatus.valueOf(rs.getString(2)), rs.getString(3)));
        }, runId);
        return Optional.of(run);
    }

    /** 이 실행을 이미 이어 받은(RESUME) 실행이 있나 — 한 실행은 한 번만 이어 받는다(확정 규칙 3) */
    public boolean hasResume(long runId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM batch_runs WHERE resumed_from_run_id = ? AND run_mode = 'RESUME')", Boolean.class, runId));
    }

    /** DB 배치가 만든 실행 줄에 그 실행을 만든 요청을 잇는다 */
    public void linkRequest(long runId, long requestId) {
        requiresNew.executeWithoutResult(status -> jdbc.update("UPDATE batch_runs SET request_id = ? WHERE id = ? AND request_id IS NULL", requestId, runId));
    }

    /**
     * 그 배치의 「실행 중」으로 남은 줄(과 그 실행의 실행 중 단계)을 실패로 닫는다(서버가 죽어 끝맺지 못한 실행). <b>그 배치의 잠금을 쥔 쪽만</b> 부른다 —
     * 잠금을 쥐었다는 것은 지금 그 배치를 돌리는 서버가 없다는 뜻이다.
     *
     * @return 닫은 줄 수
     */
    public long failInterrupted(String jobKey) {
        Long closed = requiresNew.execute(status -> {
            Instant now = BatchJdbc.now();
            jdbc.update("UPDATE batch_run_steps s SET status = 'FAIL', finished_at = ? FROM batch_runs r "
                    + "WHERE s.run_id = r.id AND r.job_key = ? AND r.status = 'RUNNING' AND s.status = 'RUNNING'", ts(now), jobKey);
            long count = jdbc.update("UPDATE batch_runs SET status = 'FAIL', finished_at = ?, message_key = ?, message_args = NULL WHERE job_key = ? AND status = 'RUNNING'",
                    ts(now), INTERRUPTED_KEY, jobKey);
            if (count > 0) {
                jdbc.update("UPDATE batch_jobs SET last_status = 'FAIL', last_message_key = ?, last_message_args = NULL WHERE job_key = ? AND last_status = 'RUNNING'",
                        INTERRUPTED_KEY, jobKey);
            }
            return count;
        });
        return closed == null ? 0 : closed;
    }

    /** 실행 중으로 남아 있는 로그가 있는 배치 키 */
    public Set<String> runningJobKeys() {
        return new HashSet<>(jdbc.queryForList("SELECT DISTINCT job_key FROM batch_runs WHERE status = 'RUNNING'", String.class));
    }

    /** 그 배치가 지금 실행 중인가(실행 중 줄이 있나) */
    public boolean isRunning(String jobKey) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM batch_runs WHERE job_key = ? AND status = 'RUNNING')", Boolean.class, jobKey));
    }

    /** 목록의 마지막 실행 요약(Java 배치만 행이 있다 — 없으면 아무것도 바뀌지 않는다) */
    private void updateLastRun(String jobKey, Instant at, BatchRunStatus status, Long affected, MessageRef message, String error) {
        jdbc.update("UPDATE batch_jobs SET last_run_at = ?, last_status = ?, last_affected_count = ?, last_message_key = ?, last_message_args = ?, last_error = ? "
                        + "WHERE job_key = ?",
                ts(at), status.name(), affected, message == null ? null : message.key(), message == null ? null : BatchMessageCodec.encodeArgs(message), error, jobKey);
    }
}
