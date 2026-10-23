package com.community.batch.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchLockService.BatchLock;
import com.community.batch.core.BatchRequestQueue.ClaimedRequest;
import com.community.batch.core.BatchTestKit.ItemJob;
import com.community.batch.entity.BatchRequestKind;
import com.community.batch.entity.BatchRequestStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 실행 요청 표(docs/domain/commerce.md 15-2, 결정 6·7) — 같은 배치에 끝나지 않은 요청 하나, {@code SKIP LOCKED} 집기, 집을 때 실행 중이면 거절, 남은 RUNNING 요청 정리. 실제 PostgreSQL.
 */
class BatchRequestQueueTest {

    private static BatchTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = new BatchTestKit();
    }

    private static BatchRequestExecutor executor(ItemJob job) {
        BatchRunner runner = kit.runner(job);
        return new BatchRequestExecutor(kit.queue, runner, BatchRegistry.forJobs(List.of(job)), new DbBatchCaller(kit.dataSource), kit.recorder);
    }

    private static long enqueue(String key) {
        return kit.queue.enqueue(key, BatchRequestKind.NOW, Map.of(), null, null, null).orElseThrow();
    }

    private static Map<String, Object> request(long id) {
        return kit.jdbc.queryForMap("SELECT * FROM batch_run_requests WHERE id = ?", id);
    }

    @Test
    void 같은_배치에_끝나지_않은_요청은_하나뿐이다() {
        String key = BatchTestKit.newKey("open");
        String other = BatchTestKit.newKey("open");
        long first = enqueue(key);

        assertThat(kit.queue.enqueue(key, BatchRequestKind.NOW, Map.of(), null, null, null)).as("대기 중 요청이 있으면 두 번째는 들어가지 않는다").isEmpty();
        assertThat(kit.queue.hasOpen(key)).isTrue();
        assertThat(kit.queue.enqueue(other, BatchRequestKind.NOW, Map.of(), null, null, null)).as("다른 배치는 된다").isPresent();

        ClaimedRequest claimed = kit.queue.claimNext(List.of(key)).orElseThrow();
        assertThat(claimed.id()).isEqualTo(first);
        assertThat(kit.queue.enqueue(key, BatchRequestKind.NOW, Map.of(), null, null, null)).as("실행 중(RUNNING) 요청이 있어도 안 된다").isEmpty();

        kit.queue.done(first, null);
        assertThat(kit.queue.enqueue(key, BatchRequestKind.NOW, Map.of(), null, null, null)).as("끝나면 다시 넣을 수 있다").isPresent();
        cleanUp(key, other);
    }

    @Test
    void 다른_연결이_잠근_요청은_건너뛰고_다음_요청을_집는다() throws Exception {
        String a = BatchTestKit.newKey("skip");
        String b = BatchTestKit.newKey("skip");
        long first = enqueue(a);
        long second = enqueue(b);

        try (Connection other = kit.dataSource.getConnection()) {
            other.setAutoCommit(false);
            try (PreparedStatement lock = other.prepareStatement("SELECT id FROM batch_run_requests WHERE id = ? FOR UPDATE SKIP LOCKED")) {
                lock.setLong(1, first);
                try (ResultSet rs = lock.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                }
            }
            Optional<ClaimedRequest> claimed = kit.queue.claimNext(List.of(a, b));
            assertThat(claimed).map(ClaimedRequest::id).as("잠긴 첫 요청을 건너뛰고 둘째를 집는다").contains(second);
            assertThat(kit.queue.claimNext(List.of(a, b))).as("남은 것은 잠긴 것뿐").isEmpty();
            other.rollback();
        }
        assertThat(kit.queue.claimNext(List.of(a, b))).map(ClaimedRequest::id).contains(first);
        cleanUp(a, b);
    }

    @Test
    void 두_워커가_동시에_집어도_한_요청을_두_번_집지_않는다() throws Exception {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String key = BatchTestKit.newKey("race");
            keys.add(key);
            enqueue(key);
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<List<Long>>> workers = new ArrayList<>();
        for (int w = 0; w < 2; w++) {
            workers.add(pool.submit(() -> {
                go.await();
                List<Long> mine = new ArrayList<>();
                Optional<ClaimedRequest> next;
                while ((next = kit.queue.claimNext(keys)).isPresent()) {
                    mine.add(next.get().id());
                }
                return mine;
            }));
        }
        go.countDown();
        List<Long> all = new ArrayList<>();
        for (Future<List<Long>> worker : workers) {
            all.addAll(worker.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        Set<Long> unique = new HashSet<>(all);
        assertThat(all).as("집은 수 = 요청 수, 겹침 없음").hasSize(30);
        assertThat(unique).hasSize(30);
        for (Long id : unique) {
            assertThat(request(id).get("status")).isEqualTo("RUNNING");
            assertThat(request(id).get("node_id")).isEqualTo(BatchTestKit.NODE_ID);
        }
        cleanUp(keys.toArray(String[]::new));
    }

    @Test
    void 집을_때_같은_배치가_실행_중이면_거절하고_실행하지_않는다() {
        String key = BatchTestKit.newKey("busy");
        ItemJob job = kit.singleStepJob(key);
        kit.items(job.groupOf("main"), 2);
        BatchRequestExecutor executor = executor(job);
        long requestId = enqueue(key);

        try (BatchLock held = kit.locks.tryAcquire(key).orElseThrow()) { // 다른 서버가 이 배치를 돌리는 중
            ClaimedRequest claimed = kit.queue.claimNext(List.of(key)).orElseThrow();
            assertThat(executor.execute(claimed)).isEqualTo(BatchRequestStatus.REJECTED);
        }
        Map<String, Object> rejected = request(requestId);
        assertThat(rejected.get("status")).isEqualTo("REJECTED");
        assertThat(rejected.get("message_key")).isEqualTo(BatchRequestExecutor.RUNNING_KEY);
        assertThat(rejected.get("run_id")).isNull();
        assertThat(kit.count("SELECT count(*) FROM batch_runs WHERE job_key = ?", key)).as("실행 줄이 생기지 않았다").isZero();

        long next = enqueue(key);
        assertThat(executor.execute(kit.queue.claimNext(List.of(key)).orElseThrow())).isEqualTo(BatchRequestStatus.DONE);
        Map<String, Object> done = request(next);
        assertThat(done.get("status")).isEqualTo("DONE");
        Long runId = (Long) done.get("run_id");
        assertThat(runId).isNotNull();
        assertThat(kit.run(runId).get("request_id")).isEqualTo(next);
        assertThat(kit.run(runId).get("trigger_type")).isEqualTo("MANUAL");
    }

    @Test
    void 집은_서버가_사라져_RUNNING_으로_남은_요청을_정리한다() {
        String lost = BatchTestKit.newKey("stale");
        String finished = BatchTestKit.newKey("stale");
        String running = BatchTestKit.newKey("stale");
        String fresh = BatchTestKit.newKey("stale");
        String locked = BatchTestKit.newKey("stale");
        long lostRequest = staleRequest(lost, null, "10 minutes");
        long finishedRun = runRow(finished, "OK");
        long finishedRequest = staleRequest(finished, finishedRun, "10 minutes");
        long runningRun = runRow(running, "RUNNING");
        long runningRequest = staleRequest(running, runningRun, "10 minutes");
        long freshRequest = staleRequest(fresh, null, "1 second");
        long lockedRequest = staleRequest(locked, null, "10 minutes");

        try (BatchLock held = kit.locks.tryAcquire(locked).orElseThrow()) { // 이 배치는 지금 누가 돌리고 있다
            kit.queue.sweepStale(Duration.ofMinutes(2), kit.locks);
        }

        assertThat(request(lostRequest).get("status")).as("실행도 없고 잠금도 비었다 — 거절로 닫는다").isEqualTo("REJECTED");
        assertThat(request(lostRequest).get("message_key")).isEqualTo(BatchRequestQueue.INTERRUPTED_KEY);
        assertThat(request(finishedRequest).get("status")).as("실행이 이미 끝났다 — 끝남").isEqualTo("DONE");
        assertThat(request(finishedRequest).get("run_id")).isEqualTo(finishedRun);
        assertThat(request(runningRequest).get("status")).as("실행이 아직 실행 중 — 그대로").isEqualTo("RUNNING");
        assertThat(request(freshRequest).get("status")).as("방금 집은 요청 — 그대로").isEqualTo("RUNNING");
        assertThat(request(lockedRequest).get("status")).as("잠금이 잡혀 있다 — 그대로").isEqualTo("RUNNING");

        kit.jdbc.update("UPDATE batch_runs SET status = 'FAIL', finished_at = now() WHERE id = ?", runningRun);
        kit.queue.sweepStale(Duration.ofMinutes(2), kit.locks);
        assertThat(request(runningRequest).get("status")).as("그 실행이 닫히면 다음 정리에서 끝남").isEqualTo("DONE");
        cleanUp(lost, finished, running, fresh, locked);
    }

    private static long staleRequest(String key, Long runId, String age) {
        long id = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO batch_run_requests (id, job_key, kind, status, run_id, requested_at, started_at, node_id) "
                + "VALUES (?, ?, 'NOW', 'RUNNING', ?, now() - ?::interval, now() - ?::interval, 999)", id, key, runId, age, age);
        return id;
    }

    private static long runRow(String key, String status) {
        long id = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO batch_runs (id, job_key, status, trigger_type, started_at, finished_at, source) VALUES (?, ?, ?, 'MANUAL', now(), "
                + "CASE WHEN ? = 'RUNNING' THEN NULL ELSE now() END, 'JAVA')", id, key, status, status);
        return id;
    }

    /** 다른 시험의 집기에 걸리지 않게 이 시험의 끝나지 않은 요청을 닫는다 */
    private static void cleanUp(String... keys) {
        for (String key : keys) {
            kit.jdbc.update("UPDATE batch_run_requests SET status = 'REJECTED', started_at = coalesce(started_at, now()), finished_at = now() "
                    + "WHERE job_key = ? AND status IN ('PENDING', 'RUNNING')", key);
        }
    }
}
