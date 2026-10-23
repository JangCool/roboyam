package com.community.batch.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchRunner.Outcome;
import com.community.batch.core.BatchRunner.RunReport;
import com.community.batch.core.BatchTestKit.ItemJob;
import com.community.batch.entity.BatchRequestKind;
import com.community.batch.entity.BatchRunStatus;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 덩어리 처리 도우미와 실행 건수 규칙(docs/domain/commerce.md 15-3 성능 규칙, docs/features/batch-contract.md 「0-2 보강」 확정 규칙 1·2) — 실제 PostgreSQL.
 */
class BatchChunkProcessingTest {

    private static BatchTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = new BatchTestKit();
    }

    @Test
    void 덩어리마다_저장_확정한다_중간에_실패해도_앞_덩어리는_남는다() {
        String key = BatchTestKit.newKey("commit");
        kit.setting(key, "chunk_size", 3);
        ItemJob job = kit.singleStepJob(key);
        List<Long> items = kit.items(job.groupOf("main"), 10);
        job.failReadAt.put("main", 3); // 세 번째 덩어리를 읽다가 실패

        RunReport report = kit.runner(job).runRequested(BatchTestKit.now(job.definition));

        assertThat(report.outcome()).isEqualTo(Outcome.FINISHED);
        assertThat(report.status()).isEqualTo(BatchRunStatus.FAIL);
        for (int i = 0; i < 6; i++) {
            assertThat(kit.touched(items.get(i))).as("앞 두 덩어리(6건)는 확정됐다").isEqualTo(1);
        }
        for (int i = 6; i < 10; i++) {
            assertThat(kit.touched(items.get(i))).as("실패한 뒤의 대상은 손대지 않았다").isZero();
        }
        Map<String, Object> step = kit.step(report.runId(), "main");
        assertThat(step.get("status")).isEqualTo("FAIL");
        assertThat(step.get("processed_count")).isEqualTo(6L);
        assertThat(step.get("checkpoint")).isEqualTo(String.valueOf(items.get(5)));
        Map<String, Object> run = kit.run(report.runId());
        assertThat(run.get("checkpoint")).isEqualTo(String.valueOf(items.get(5)));
        assertThat(run.get("affected_count")).isEqualTo(6L);
        assertThat((String) run.get("error_message")).startsWith("IllegalStateException");
    }

    @Test
    void 실패한_덩어리만_한_건씩_다시_처리하고_실패한_건만_기록한다() {
        String key = BatchTestKit.newKey("isolate");
        kit.setting(key, "chunk_size", 3);
        ItemJob job = kit.singleStepJob(key);
        List<Long> items = kit.items(job.groupOf("main"), 9);
        long bad = items.get(4); // 두 번째 덩어리의 가운데
        job.poison.add(bad);

        RunReport report = kit.runner(job).runRequested(BatchTestKit.now(job.definition));

        assertThat(report.status()).isEqualTo(BatchRunStatus.OK); // 한 건 실패가 전체를 멈추지 않는다(한도 안)
        for (Long item : items) {
            assertThat(kit.touched(item)).as("덩어리가 되돌려진 뒤 한 건씩 다시 — 성공 건은 정확히 한 번").isEqualTo(item == bad ? 0 : 1);
        }
        List<Map<String, Object>> failures = kit.failuresOf(report.runId());
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).get("target_id")).isEqualTo(String.valueOf(bad));
        assertThat(failures.get(0).get("target_type")).isEqualTo("ZZ_ITEM");
        assertThat(failures.get(0).get("error_code")).isEqualTo("ZZ_POISON");
        assertThat(failures.get(0).get("attempts")).isEqualTo(1);
        assertThat(failures.get(0).get("resolved")).isEqualTo(false);
        Map<String, Object> step = kit.step(report.runId(), "main");
        assertThat(step.get("read_count")).isEqualTo(9L);
        assertThat(step.get("processed_count")).isEqualTo(8L);
        assertThat(step.get("failed_count")).isEqualTo(1L);
        assertThat(step.get("checkpoint")).isEqualTo(String.valueOf(items.get(8)));
    }

    @Test
    void 성공_건은_건별로_기록하지_않고_덩어리마다_이력을_한_번_고친다() {
        String key = BatchTestKit.newKey("once");
        kit.setting(key, "chunk_size", 3);
        ItemJob job = kit.singleStepJob(key);
        List<Long> items = kit.items(job.groupOf("main"), 10); // 3 + 3 + 3 + 1 = 덩어리 넷
        List<String> progress = new CopyOnWriteArrayList<>();
        BatchRunRecorder counting = new BatchRunRecorder(kit.dataSource, kit.transactions, kit.ids, BatchTestKit.NODE_ID) {
            @Override
            public void addStepProgress(long runId, String step, long read, long processed, long failed, long skipped, String checkpoint) {
                progress.add(runId + ":" + step + ":" + read + ":" + checkpoint);
                super.addStepProgress(runId, step, read, processed, failed, skipped, checkpoint);
            }
        };

        RunReport report = kit.runner(counting, job).runRequested(BatchTestKit.now(job.definition));

        assertThat(report.status()).isEqualTo(BatchRunStatus.OK);
        assertThat(progress).as("덩어리 넷 = 단계 이력 갱신 네 번(덩어리 저장 확정과 같은 트랜잭션)").hasSize(4);
        assertThat(progress).allMatch(line -> line.startsWith(report.runId() + ":main:"));
        assertThat(progress.get(3)).endsWith(":1:" + items.get(9));
        assertThat(kit.failuresOf(report.runId())).as("성공 건은 건별 줄이 없다").isEmpty();
        assertThat(kit.step(report.runId(), "main").get("processed_count")).isEqualTo(10L);
    }

    @Test
    void 실패_수가_한도를_넘으면_실행을_FAIL_로_멈춘다() {
        String key = BatchTestKit.newKey("limit");
        kit.setting(key, "chunk_size", 2);
        kit.setting(key, "failure_limit", 2);
        ItemJob job = kit.singleStepJob(key);
        List<Long> items = kit.items(job.groupOf("main"), 10);
        job.poison.addAll(List.of(items.get(0), items.get(2), items.get(4), items.get(6)));

        RunReport report = kit.runner(job).runRequested(BatchTestKit.now(job.definition));

        assertThat(report.status()).isEqualTo(BatchRunStatus.FAIL);
        Map<String, Object> run = kit.run(report.runId());
        assertThat(run.get("message_key")).isEqualTo(BatchFailureLimitException.MESSAGE_KEY);
        assertThat(run.get("message_args")).isEqualTo("[\"2\"]");
        assertThat(run.get("error_message")).isNull();
        assertThat(run.get("failed_count")).isEqualTo(3L);
        assertThat(kit.failuresOf(report.runId())).as("한도(2)를 넘는 세 번째 실패에서 멈춘다").hasSize(3);
        for (int i = 6; i < 10; i++) {
            assertThat(kit.touched(items.get(i))).as("멈춘 뒤의 덩어리는 처리하지 않았다").isZero();
        }
        assertThat(kit.step(report.runId(), "main").get("status")).isEqualTo("FAIL");
    }

    @Test
    void 실행_건수는_주_단계의_건수이고_실패는_단계_합이다() {
        String key = BatchTestKit.newKey("counts");
        ItemJob job = kit.job(key, List.of(new BatchStepDefinition("prepare", true), new BatchStepDefinition("apply", true)), "apply", List.of(), false);
        List<Long> prepare = kit.items(job.groupOf("prepare"), 4);
        List<Long> apply = kit.items(job.groupOf("apply"), 6);
        job.poison.add(prepare.get(1));
        job.poison.addAll(List.of(apply.get(0), apply.get(5)));

        RunReport report = kit.runner(job).runRequested(BatchTestKit.now(job.definition));

        Map<String, Object> run = kit.run(report.runId());
        assertThat(run.get("status")).isEqualTo("OK");
        assertThat(run.get("read_count")).as("읽음 = 주 단계(apply)").isEqualTo(6L);
        assertThat(run.get("affected_count")).as("처리 = 주 단계").isEqualTo(4L);
        assertThat(run.get("skipped_count")).isEqualTo(0L);
        assertThat(run.get("failed_count")).as("실패 = 단계 합(1 + 2)").isEqualTo(3L);
        assertThat(kit.step(report.runId(), "prepare").get("failed_count")).isEqualTo(1L);
        assertThat(kit.step(report.runId(), "apply").get("failed_count")).isEqualTo(2L);
    }

    @Test
    void 고를_수_없는_단계는_단계를_골라_실행해도_늘_돈다() {
        String key = BatchTestKit.newKey("steps");
        ItemJob job = kit.job(key, List.of(new BatchStepDefinition("fetch", false), new BatchStepDefinition("compare", true), new BatchStepDefinition("report", true)),
                "compare", List.of(), false);
        List<Long> fetch = kit.items(job.groupOf("fetch"), 2);
        List<Long> compare = kit.items(job.groupOf("compare"), 2);
        List<Long> report = kit.items(job.groupOf("report"), 2);

        RunReport result = kit.runner(job).runRequested(new BatchRunner.RunRequest(job.definition, BatchRequestKind.NOW, null, BatchParameters.empty(), List.of("report"),
                null, null));

        assertThat(kit.step(result.runId(), "fetch").get("status")).as("고를 수 없는 단계 — 고르지 않아도 돈다").isEqualTo("OK");
        assertThat(kit.step(result.runId(), "compare").get("status")).as("고를 수 있는데 고르지 않은 단계").isEqualTo("SKIP");
        assertThat(kit.step(result.runId(), "report").get("status")).isEqualTo("OK");
        assertThat(kit.touched(fetch.get(0))).isEqualTo(1);
        assertThat(kit.touched(compare.get(0))).isZero();
        assertThat(kit.touched(report.get(0))).isEqualTo(1);
        assertThat(((Number) kit.step(result.runId(), "compare").get("step_order")).intValue()).isEqualTo(2);
    }

    @Test
    void 실패_건의_문구는_이메일_토큰_칸_값을_가려_남긴다() {
        String key = BatchTestKit.newKey("mask");
        ItemJob job = kit.singleStepJob(key);
        List<Long> items = kit.items(job.groupOf("main"), 1);
        job.poison.add(items.get(0));
        job.poisonMessage = "user kim.cool@example.com 처리 실패 token=abc.def.ghi Key (email)=(kim.cool@example.com) already exists";

        RunReport report = kit.runner(job).runRequested(BatchTestKit.now(job.definition));

        String message = (String) kit.failuresOf(report.runId()).get(0).get("message");
        assertThat(message).doesNotContain("kim.cool").doesNotContain("abc.def.ghi").contains("token=(hidden)").contains("(email)=(hidden)");
    }
}
