package com.community.batch.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.config.BatchProperties;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchDefinition;
import com.community.batch.core.BatchFailureRecorder;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchLockService;
import com.community.batch.core.BatchRegistry;
import com.community.batch.core.BatchRequestExecutor;
import com.community.batch.core.BatchRequestQueue;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchRunRecorder;
import com.community.batch.core.BatchRunner;
import com.community.batch.core.DbBatchCaller;
import com.community.batch.core.WorkerHeartbeats;
import com.community.batch.entity.BatchRequestKind;
import com.community.batch.entity.BatchSource;
import com.community.batch.repository.master.BatchJobRepository;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * 배치 설정은 {@code roboyam.batch.enabled} 하나다(결정 6 — 예약 실행·실행 요청 집기·워커가 함께 켜지고 꺼진다). 끈 서버는 요청을 집지 않고(대기로 남아 다른 서버가 집는다)
 * 예약도 하지 않는다. 켠 서버는 집어서 실행하고 워커 기록을 남긴다. 실제 PostgreSQL(커머스 시험 DB).
 */
class BatchEnabledSwitchTest {

    private static final int NODE_ID = 778;
    private static final String RUN_SUFFIX = Long.toString(System.nanoTime() % 1_000_000L, 36);
    private static final AtomicInteger KEYS = new AtomicInteger();

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager transactions;
    private static SnowflakeIdGenerator ids;
    private static BatchLockService locks;
    private static BatchRunRecorder recorder;
    private static BatchRequestQueue queue;

    @BeforeAll
    static void setUp() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
        transactions = new DataSourceTransactionManager(dataSource);
        ids = new SnowflakeIdGenerator(NODE_ID);
        locks = new BatchLockService(dataSource);
        recorder = new BatchRunRecorder(dataSource, transactions, ids, NODE_ID);
        queue = new BatchRequestQueue(dataSource, transactions, ids, NODE_ID);
    }

    private static BatchProperties properties(boolean enabled) {
        return new BatchProperties(enabled, "Asia/Seoul", 30, 60, 2, 2000, 120, 30);
    }

    /** 한 번 돌면 횟수만 세는 시험 작업 */
    private static final class CountingJob implements BatchJob {
        final String key = ("zz-switch-" + RUN_SUFFIX + "-" + KEYS.incrementAndGet()).toLowerCase();
        final AtomicInteger runs = new AtomicInteger();

        @Override
        public BatchDefinition definition() {
            return new BatchDefinition() {
                @Override
                public String key() {
                    return key;
                }

                @Override
                public BatchSource source() {
                    return BatchSource.JAVA;
                }
            };
        }

        @Override
        public BatchResult run(BatchContext context) {
            runs.incrementAndGet();
            context.step("main").record(1, 1, 0);
            return BatchResult.of(new MessageRef("batch.step.main"));
        }
    }

    private static BatchRequestPoller poller(boolean enabled, CountingJob job, WorkerHeartbeats heartbeats) {
        BatchRegistry registry = BatchRegistry.forJobs(List.of(job));
        BatchRunner runner = new BatchRunner(locks, recorder, new BatchFailureRecorder(dataSource, transactions, ids), registry, transactions);
        BatchRequestExecutor executor = new BatchRequestExecutor(queue, runner, registry, new DbBatchCaller(dataSource), recorder);
        return new BatchRequestPoller(properties(enabled), executor, heartbeats);
    }

    private static String requestStatus(long requestId) {
        return jdbc.queryForObject("SELECT status FROM batch_run_requests WHERE id = ?", String.class, requestId);
    }

    @Test
    void 배치를_끈_서버는_요청을_집지_않고_워커_기록도_남기지_않는다() {
        CountingJob job = new CountingJob();
        long requestId = queue.enqueue(job.key, BatchRequestKind.NOW, Map.of(), null, null, null).orElseThrow();
        WorkerHeartbeats heartbeats = new WorkerHeartbeats(dataSource, NODE_ID, Duration.ofSeconds(30), java.time.Clock.systemUTC());
        BatchRequestPoller off = poller(false, job, heartbeats);

        off.start();
        assertThat(off.isStarted()).isFalse();
        assertThat(off.poll()).isZero();

        assertThat(requestStatus(requestId)).as("대기로 남아 배치를 켠 다른 서버가 집는다").isEqualTo("PENDING");
        assertThat(job.runs).hasValue(0);
        jdbc.update("UPDATE batch_run_requests SET status = 'REJECTED', started_at = now(), finished_at = now() WHERE id = ?", requestId);
    }

    @Test
    void 배치를_켠_서버는_요청을_집어_실행하고_워커_기록을_남긴다() throws Exception {
        CountingJob job = new CountingJob();
        long requestId = queue.enqueue(job.key, BatchRequestKind.NOW, Map.of(), null, null, null).orElseThrow();
        WorkerHeartbeats heartbeats = new WorkerHeartbeats(dataSource, NODE_ID, Duration.ofSeconds(30), java.time.Clock.systemUTC());
        BatchRequestPoller on = poller(true, job, heartbeats);
        try {
            on.start();
            assertThat(on.isStarted()).isTrue();
            assertThat(on.poll()).isEqualTo(1);

            Instant deadline = Instant.now().plusSeconds(30);
            while (!"DONE".equals(requestStatus(requestId)) && Instant.now().isBefore(deadline)) {
                Thread.sleep(50);
            }
            assertThat(requestStatus(requestId)).isEqualTo("DONE");
            assertThat(job.runs).hasValue(1);
            Map<String, Object> beat = jdbc.queryForMap("SELECT * FROM worker_heartbeats WHERE worker_key = ?", BatchRequestPoller.WORKER_KEY);
            assertThat(beat.get("last_count")).isEqualTo(1L);
            assertThat(beat.get("node_id")).isEqualTo(NODE_ID);
        } finally {
            on.shutdown();
        }
    }

    @Test
    void 배치를_끈_서버는_예약도_하지_않고_켠_서버는_예약을_맞춘다() {
        List<String> calls = new CopyOnWriteArrayList<>();
        BatchJobRepository jobs = (BatchJobRepository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {BatchJobRepository.class},
                (proxy, method, args) -> {
                    calls.add(method.getName());
                    return method.getName().equals("findAll") ? List.of() : null;
                });
        BatchRunner runner = new BatchRunner(locks, recorder, new BatchFailureRecorder(dataSource, transactions, ids), BatchRegistry.forJobs(List.of()), transactions);

        BatchScheduler off = new BatchScheduler(properties(false), jobs, runner, queue, locks);
        off.start();
        off.reconcile();
        assertThat(calls).as("끈 서버는 예약 설정을 읽지도 않는다").isEmpty();
        off.shutdown();

        BatchScheduler on = new BatchScheduler(properties(true), jobs, runner, queue, locks);
        try {
            on.start();
            assertThat(calls).as("켠 서버는 예약을 맞춘다(batch_jobs 를 읽는다)").contains("findAll");
        } finally {
            on.shutdown();
        }
    }

    @Test
    void 설정_이름은_roboyam_batch_enabled_하나이고_워커_스케줄러는_스레드를_여럿_둔다() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties settings = yaml.getObject();

        assertThat(settings).containsKey("roboyam.batch.enabled");
        assertThat(settings).doesNotContainKey("roboyam.batch.scheduler-enabled");
        assertThat(Integer.parseInt(settings.getProperty("spring.task.scheduling.pool.size"))).as("@Scheduled 워커끼리 막지 않게").isGreaterThanOrEqualTo(2);
        assertThat(settings).containsKeys("roboyam.batch.request-poll-millis", "roboyam.batch.request-stale-seconds", "roboyam.batch.heartbeat-idle-seconds");
    }
}
