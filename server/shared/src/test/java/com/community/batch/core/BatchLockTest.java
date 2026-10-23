package com.community.batch.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchLockService.BatchLock;
import com.community.batch.core.BatchRunner.Outcome;
import com.community.batch.core.BatchRunner.RunReport;
import com.community.batch.core.BatchTestKit.ItemJob;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 같은 배치는 동시에 한 곳에서만 돈다 — DB 어드바이저리 잠금(서버 여러 대여도) + 실행 중 줄 유일 색인. 실제 PostgreSQL.
 */
class BatchLockTest {

    private static BatchTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = new BatchTestKit();
    }

    @Test
    void 잠금은_한_곳만_잡고_풀면_다시_잡을_수_있다() {
        String key = BatchTestKit.newKey("lock");
        String other = BatchTestKit.newKey("lock");
        try (BatchLock first = kit.locks.tryAcquire(key).orElseThrow()) {
            assertThat(kit.locks.tryAcquire(key)).as("다른 연결(다른 서버)은 못 잡는다").isEmpty();
            Optional<BatchLock> different = kit.locks.tryAcquire(other);
            assertThat(different).as("다른 배치는 따로 잠근다").isPresent();
            different.get().close();
        }
        Optional<BatchLock> again = kit.locks.tryAcquire(key);
        assertThat(again).isPresent();
        again.get().close();
    }

    @Test
    void 같은_배치를_동시에_실행하면_하나만_돈다() throws Exception {
        String key = BatchTestKit.newKey("single");
        ItemJob job = kit.singleStepJob(key);
        kit.items(job.groupOf("main"), 3);
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        job.beforeProcess = items -> {
            inside.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        BatchRunner runner = kit.runner(job);

        CompletableFuture<RunReport> first = CompletableFuture.supplyAsync(() -> runner.runRequested(BatchTestKit.now(job.definition)));
        assertThat(inside.await(30, TimeUnit.SECONDS)).isTrue();
        RunReport second = runner.runRequested(BatchTestKit.now(job.definition));
        release.countDown();

        assertThat(second.outcome()).as("돌고 있는 동안 두 번째는 돌지 않는다").isEqualTo(Outcome.ALREADY_RUNNING);
        assertThat(first.get(30, TimeUnit.SECONDS).outcome()).isEqualTo(Outcome.FINISHED);
        assertThat(kit.count("SELECT count(*) FROM batch_runs WHERE job_key = ?", key)).isEqualTo(1);
        assertThat(kit.locks.tryAcquire(key).map(lock -> {
            lock.close();
            return true;
        })).as("끝나면 잠금이 풀린다").contains(true);
    }
}
