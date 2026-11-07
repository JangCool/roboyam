package com.community.batch.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.batch.core.BatchRunRecorder.StartCommand;
import com.community.batch.core.BatchRunRecorder.Started;
import com.community.batch.core.BatchRunRecorder.StepRow;
import com.community.batch.entity.BatchRequestKind;
import com.community.batch.entity.BatchRunMode;
import com.community.batch.entity.BatchStepStatus;
import com.community.batch.entity.BatchTriggerType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 배치 core 기록기를 JPA·QueryDSL 로 옮긴 뒤에도 JDBC 때와 같게 도는지(2026-11-07 3단계) — 유일 위반만 「빈 값」, 다른 무결성 위반은 예외, {@code ON CONFLICT DO NOTHING} 은 부르는
 * 트랜잭션을 깨지 않는다, 실행 중 줄 유일 색인 위반은 {@link DataIntegrityViolationException}, 죽은 실행 닫기, 요청에 이어 두지 않은 실행으로 요청 정리. 실제 PostgreSQL.
 */
class BatchRecordJpaTest {

    private static BatchTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = new BatchTestKit();
    }

    private static StartCommand manual(String key, List<StepRow> steps) {
        return new StartCommand(key, BatchTriggerType.MANUAL, null, null, BatchRunMode.NORMAL, null, null, null, steps);
    }

    @Test
    void 끝나지_않은_요청이_있으면_enqueue_는_빈_값이고_다른_무결성_위반은_예외다() {
        String key = BatchTestKit.newKey("dup");
        assertThat(kit.queue.enqueue(key, BatchRequestKind.NOW, Map.of(), null, null, null)).isPresent();
        assertThat(kit.queue.enqueue(key, BatchRequestKind.NOW, Map.of(), null, null, null)).as("유일 색인 위반 — 빈 값").isEmpty();

        String other = BatchTestKit.newKey("fk");
        assertThatThrownBy(() -> kit.queue.enqueue(other, BatchRequestKind.NOW, Map.of(), null, null, -1L)).as("없는 운영자(외래 키) — 빈 값이 아니라 예외")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(kit.count("SELECT count(*) FROM batch_run_requests WHERE job_key = ?", other)).isZero();
        closeOpen(key);
    }

    @Test
    void enqueueIfIdle_은_한_줄만_넣고_겹쳐도_부르는_트랜잭션을_깨지_않는다() {
        String key = BatchTestKit.newKey("idle");
        TransactionTemplate tx = new TransactionTemplate(kit.transactions);
        Optional<Long>[] results = tx.execute(status -> {
            @SuppressWarnings("unchecked")
            Optional<Long>[] both = new Optional[] {kit.queue.enqueueIfIdle(key, null), kit.queue.enqueueIfIdle(key, null)};
            assertThat(kit.queue.hasOpen(key)).as("겹친 뒤에도 같은 트랜잭션에서 읽을 수 있다").isTrue();
            return both;
        });
        assertThat(results[0]).isPresent();
        assertThat(results[1]).as("이미 있으면 빈 값").isEmpty();
        assertThat(kit.count("SELECT count(*) FROM batch_run_requests WHERE job_key = ?", key)).isEqualTo(1);
        assertThat(kit.queue.enqueueIfIdle(key, null)).as("트랜잭션 밖에서도 — 그 문장만의 트랜잭션").isEmpty();
        closeOpen(key);
    }

    @Test
    void 같은_배치가_실행_중이면_시작이_DataIntegrityViolationException_이다() {
        String key = BatchTestKit.newKey("twice");
        Started first = kit.recorder.start(manual(key, List.of(new StepRow("main", 1, BatchStepStatus.PENDING))));
        assertThatThrownBy(() -> kit.recorder.start(manual(key, List.of(new StepRow("main", 1, BatchStepStatus.PENDING))))).as("실행 중 줄 유일 색인")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(kit.count("SELECT count(*) FROM batch_runs WHERE job_key = ?", key)).as("두 번째 줄은 남지 않는다").isEqualTo(1);
        assertThat(kit.count("SELECT count(*) FROM batch_run_steps WHERE run_id = ?", first.runId())).isEqualTo(1);
        kit.recorder.failInterrupted(key);
    }

    @Test
    void 죽은_실행은_실행_중_단계와_마지막_실행_요약까지_실패로_닫는다() {
        String key = BatchTestKit.newKey("dead");
        kit.jdbc.update("INSERT INTO batch_jobs (job_key, enabled, cron) VALUES (?, false, '0 0 * * *')", key);
        Started started = kit.recorder.start(manual(key, List.of(new StepRow("first", 1, BatchStepStatus.PENDING), new StepRow("second", 2, BatchStepStatus.PENDING),
                new StepRow("third", 3, BatchStepStatus.SKIP))));
        kit.recorder.stepRunning(started.runId(), "first");
        assertThat(kit.jdbc.queryForObject("SELECT last_status FROM batch_jobs WHERE job_key = ?", String.class, key)).isEqualTo("RUNNING");
        assertThat(kit.step(started.runId(), "third").get("finished_at")).as("SKIP 단계는 시작할 때 닫혀 있다").isNotNull();

        assertThat(kit.recorder.runningJobKeys()).contains(key);
        assertThat(kit.recorder.failInterrupted(key)).as("닫은 실행 줄 수").isEqualTo(1); // 실행기의 sweepInterrupted 가 잠금을 잡고 부르는 것(다른 시험의 줄은 건드리지 않게 키 하나만)
        assertThat(kit.recorder.isRunning(key)).isFalse();

        Map<String, Object> run = kit.run(started.runId());
        assertThat(run.get("status")).isEqualTo("FAIL");
        assertThat(run.get("message_key")).isEqualTo(BatchRunRecorder.INTERRUPTED_KEY);
        assertThat(run.get("finished_at")).isNotNull();
        assertThat(kit.step(started.runId(), "first").get("status")).as("실행 중 단계 — 실패").isEqualTo("FAIL");
        assertThat(kit.step(started.runId(), "second").get("status")).as("대기 단계 — 그대로").isEqualTo("PENDING");
        assertThat(kit.jdbc.queryForObject("SELECT last_status FROM batch_jobs WHERE job_key = ?", String.class, key)).isEqualTo("FAIL");
        assertThat(kit.jdbc.queryForObject("SELECT last_message_key FROM batch_jobs WHERE job_key = ?", String.class, key)).isEqualTo(BatchRunRecorder.INTERRUPTED_KEY);
        kit.jdbc.update("DELETE FROM batch_jobs WHERE job_key = ?", key);
    }

    @Test
    void 요청에_이어_두지_않은_실행이_끝났으면_그_실행으로_요청을_끝낸다() {
        String key = BatchTestKit.newKey("orphan");
        long requestId = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO batch_run_requests (id, job_key, kind, status, requested_at, started_at, node_id) "
                + "VALUES (?, ?, 'NOW', 'RUNNING', now() - interval '10 minutes', now() - interval '10 minutes', 999)", requestId, key);
        long older = kit.ids.nextId();
        long newer = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO batch_runs (id, job_key, status, trigger_type, started_at, finished_at, source, request_id) "
                + "VALUES (?, ?, 'FAIL', 'MANUAL', now() - interval '9 minutes', now(), 'JAVA', ?)", older, key, requestId);
        kit.jdbc.update("INSERT INTO batch_runs (id, job_key, status, trigger_type, started_at, finished_at, source, request_id) "
                + "VALUES (?, ?, 'OK', 'MANUAL', now() - interval '5 minutes', now(), 'JAVA', ?)", newer, key, requestId);

        kit.queue.sweepStale(Duration.ofMinutes(2), kit.locks);

        Map<String, Object> request = kit.jdbc.queryForMap("SELECT status, run_id FROM batch_run_requests WHERE id = ?", requestId);
        assertThat(request.get("status")).isEqualTo("DONE");
        assertThat(request.get("run_id")).as("그 요청이 만든 가장 최근 실행").isEqualTo(newer);
    }

    /** 다른 시험의 집기에 걸리지 않게 이 시험의 끝나지 않은 요청을 닫는다 */
    private static void closeOpen(String key) {
        kit.jdbc.update("UPDATE batch_run_requests SET status = 'REJECTED', started_at = coalesce(started_at, now()), finished_at = now() "
                + "WHERE job_key = ? AND status IN ('PENDING', 'RUNNING')", key);
    }
}
