package com.community.batch.core;

import com.community.batch.entity.BatchSource;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * 배치 core 시험의 바탕 — 실제 PostgreSQL 시험 DB({@link CommerceTestDatabase}, 커머스 0-2 의 시험이라 Testcontainers 를 쓴다)에 core 객체를 앱과 같은 모양(데이터소스 +
 * 트랜잭션 관리자)으로 만든다. DB 를 다른 시험과 같이 쓰므로 배치 키·행 ID 는 겹치지 않게 새로 만들고 표를 비우지 않는다.
 *
 * <p>시험용 작업({@link ItemJob})은 시험 전용 표 {@code zz_batch_items}(여기서 만든다)의 행을 덩어리로 훑으며 {@code touched} 를 1 올린다 — 같은 트랜잭션에서 단계 건수가
 * 고쳐지는지, 실패한 덩어리가 되돌려지는지(되돌려지면 touched 가 0 으로 남는다) 행으로 확인할 수 있다.
 */
final class BatchTestKit {

    static final int NODE_ID = 777;

    private static final AtomicLong ITEM_IDS = new AtomicLong(8_000_000_000_000L + (System.nanoTime() % 1_000_000L) * 1_000L);
    private static final AtomicInteger KEYS = new AtomicInteger();
    private static final String RUN_SUFFIX = Long.toString(System.nanoTime() % 1_000_000L, 36);

    final DataSource dataSource;
    final DataSourceTransactionManager transactions;
    final JdbcTemplate jdbc;
    final SnowflakeIdGenerator ids;
    final BatchLockService locks;
    final BatchRunRecorder recorder;
    final BatchFailureRecorder failures;
    final BatchRequestQueue queue;

    BatchTestKit() {
        this.dataSource = CommerceTestDatabase.migrated();
        this.transactions = new DataSourceTransactionManager(dataSource);
        this.jdbc = new JdbcTemplate(dataSource);
        this.ids = new SnowflakeIdGenerator(NODE_ID);
        this.locks = new BatchLockService(dataSource);
        this.recorder = new BatchRunRecorder(dataSource, transactions, ids, NODE_ID);
        this.failures = new BatchFailureRecorder(dataSource, transactions, ids);
        this.queue = new BatchRequestQueue(dataSource, transactions, ids, NODE_ID);
        jdbc.execute("CREATE TABLE IF NOT EXISTS zz_batch_items (id bigint PRIMARY KEY, grp varchar(80) NOT NULL, touched int NOT NULL DEFAULT 0)");
    }

    /** 이 실행에서 처음 쓰는 배치 키(소문자·숫자·하이픈 40자) */
    static String newKey(String prefix) {
        return ("zz-" + prefix + "-" + RUN_SUFFIX + "-" + KEYS.incrementAndGet()).toLowerCase();
    }

    static BatchDefinition java(String key) {
        return new TestDefinition(key);
    }

    record TestDefinition(String key) implements BatchDefinition {
        @Override
        public BatchSource source() {
            return BatchSource.JAVA;
        }
    }

    BatchRunner runner(BatchJob... jobs) {
        return new BatchRunner(locks, recorder, failures, BatchRegistry.forJobs(List.of(jobs)), transactions);
    }

    BatchRunner runner(BatchRunRecorder customRecorder, BatchJob... jobs) {
        return new BatchRunner(locks, customRecorder, failures, BatchRegistry.forJobs(List.of(jobs)), transactions);
    }

    /** 작업별 설정값(batch_params) */
    void setting(String jobKey, String param, long value) {
        jdbc.update("INSERT INTO batch_params (job_key, param_key, param_value) VALUES (?, ?, ?) ON CONFLICT (job_key, param_key) DO UPDATE SET param_value = EXCLUDED.param_value",
                jobKey, param, value);
    }

    /** 무리 하나에 대상 n 개 — ID 오름차순 */
    List<Long> items(String group, int count) {
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long id = ITEM_IDS.incrementAndGet();
            jdbc.update("INSERT INTO zz_batch_items (id, grp) VALUES (?, ?)", id, group);
            created.add(id);
        }
        return created;
    }

    int touched(long itemId) {
        return jdbc.queryForObject("SELECT touched FROM zz_batch_items WHERE id = ?", Integer.class, itemId);
    }

    Map<String, Object> run(long runId) {
        return jdbc.queryForMap("SELECT * FROM batch_runs WHERE id = ?", runId);
    }

    Map<String, Object> step(long runId, String step) {
        return jdbc.queryForMap("SELECT * FROM batch_run_steps WHERE run_id = ? AND step_name = ?", runId, step);
    }

    List<Map<String, Object>> failuresOf(long runId) {
        return jdbc.queryForList("SELECT * FROM batch_run_failures WHERE run_id = ? ORDER BY id", runId);
    }

    long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    /**
     * 시험용 작업 — 단계마다 무리 {@code <group>-<단계>} 의 대상을 덩어리로 훑으며 touched 를 1 올린다. {@link #poison} 에 든 대상은 처리하면 던진다(같은 트랜잭션에서 touched 를
     * 먼저 올린 뒤 — 되돌려지는지 보려고). {@link #failReadAt} 이면 그 단계의 n 번째 읽기에서 던진다.
     */
    final class ItemJob implements BatchJob {

        final BatchDefinition definition;
        final String group;
        final List<BatchStepDefinition> steps;
        final String mainStep;
        final List<BatchParamDefinition> params;
        final boolean resumable;
        final Set<Long> poison = ConcurrentHashMap.newKeySet();
        final Map<String, Integer> failReadAt = new ConcurrentHashMap<>();
        final Map<String, List<String>> readAfterKeys = new ConcurrentHashMap<>();
        final List<BatchParameters> seenParameters = new CopyOnWriteArrayList<>();
        volatile String poisonMessage = "대상을 처리하지 못했다";
        volatile Consumer<List<Long>> beforeProcess = items -> {
        };

        ItemJob(String key, String group, List<BatchStepDefinition> steps, String mainStep, List<BatchParamDefinition> params, boolean resumable) {
            this.definition = java(key);
            this.group = group;
            this.steps = steps;
            this.mainStep = mainStep;
            this.params = params;
            this.resumable = resumable;
        }

        @Override
        public BatchDefinition definition() {
            return definition;
        }

        @Override
        public List<BatchStepDefinition> steps() {
            return steps;
        }

        @Override
        public String mainStep() {
            return mainStep;
        }

        @Override
        public List<BatchParamDefinition> params() {
            return params;
        }

        @Override
        public boolean resumable() {
            return resumable;
        }

        @Override
        public BatchResult run(BatchContext context) {
            seenParameters.add(context.parameters());
            for (BatchStepDefinition step : steps) {
                context.step(step.name()).chunks(source(step.name()), processor());
            }
            return BatchResult.of(new MessageRef("batch.step.main"));
        }

        String groupOf(String step) {
            return group + "-" + step;
        }

        ChunkSource<Long> source(String step) {
            AtomicInteger reads = new AtomicInteger();
            return new ChunkSource<>() {
                @Override
                public String targetType() {
                    return "ZZ_ITEM";
                }

                @Override
                public List<Long> next(String afterKey, int size) {
                    readAfterKeys.computeIfAbsent(step, s -> Collections.synchronizedList(new ArrayList<>())).add(afterKey);
                    Integer failAt = failReadAt.get(step);
                    if (failAt != null && reads.incrementAndGet() == failAt) {
                        throw new IllegalStateException("읽기 실패(시험)");
                    }
                    long after = afterKey == null ? 0 : Long.parseLong(afterKey);
                    return jdbc.queryForList("SELECT id FROM zz_batch_items WHERE grp = ? AND id > ? ORDER BY id LIMIT ?", Long.class, groupOf(step), after, size);
                }

                @Override
                public List<Long> byIds(List<String> targetIds) {
                    return jdbc.query(connection -> {
                        PreparedStatement statement = connection.prepareStatement("SELECT id FROM zz_batch_items WHERE id = ANY (?) ORDER BY id");
                        Array array = connection.createArrayOf("bigint", targetIds.stream().map(Long::valueOf).toArray());
                        statement.setArray(1, array);
                        return statement;
                    }, (rs, row) -> rs.getLong(1));
                }

                @Override
                public String keyOf(Long item) {
                    return String.valueOf(item);
                }

                @Override
                public String targetIdOf(Long item) {
                    return String.valueOf(item);
                }
            };
        }

        ChunkProcessor<Long> processor() {
            return items -> {
                beforeProcess.accept(items);
                jdbc.update(connection -> {
                    PreparedStatement statement = connection.prepareStatement("UPDATE zz_batch_items SET touched = touched + 1 WHERE id = ANY (?)");
                    statement.setArray(1, connection.createArrayOf("bigint", items.toArray()));
                    return statement;
                });
                for (Long item : items) {
                    if (poison.contains(item)) {
                        throw new BatchItemException("ZZ_POISON", poisonMessage);
                    }
                }
                return items.size();
            };
        }
    }

    ItemJob job(String key, List<BatchStepDefinition> steps, String mainStep, List<BatchParamDefinition> params, boolean resumable) {
        return new ItemJob(key, key, steps, mainStep, params, resumable);
    }

    /** 한 단계({@code main})짜리 작업 */
    ItemJob singleStepJob(String key) {
        return job(key, List.of(BatchStepDefinition.main()), BatchStepDefinition.MAIN, List.of(), false);
    }

    static BatchRunner.RunRequest now(BatchDefinition definition) {
        return new BatchRunner.RunRequest(definition, com.community.batch.entity.BatchRequestKind.NOW, null, BatchParameters.empty(), null, null, null);
    }
}
