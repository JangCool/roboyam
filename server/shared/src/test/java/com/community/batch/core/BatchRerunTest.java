package com.community.batch.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.batch.core.BatchRunner.Outcome;
import com.community.batch.core.BatchRunner.RunReport;
import com.community.batch.core.BatchRunner.RunRequest;
import com.community.batch.core.BatchTestKit.ItemJob;
import com.community.batch.entity.BatchRequestKind;
import com.community.batch.entity.BatchRunStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/**
 * 「실패 지점부터 다시」(RESUME)와 「실패 건만 다시」(RETRY_FAILED) — docs/domain/commerce.md 15-3, docs/features/batch-contract.md 「0-2 보강」 확정 규칙 3·4. 실제 PostgreSQL.
 */
class BatchRerunTest {

    private static BatchTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = new BatchTestKit();
    }

    private ItemJob threeStepJob(String key) {
        return kit.job(key, List.of(new BatchStepDefinition("select", false), new BatchStepDefinition("process", true), new BatchStepDefinition("notify", true)),
                "process", List.of(new BatchParamDefinition("baseDate", BatchParamType.DATE, false)), true);
    }

    private static RunRequest request(ItemJob job, BatchRequestKind kind, BatchParameters parameters, Long sourceRunId) {
        return new RunRequest(job.definition, kind, null, parameters, null, sourceRunId, null);
    }

    @Test
    void 실패_지점부터_다시는_끝난_단계를_건너뛰고_체크포인트_다음부터_같은_파라미터로_돈다() {
        String key = BatchTestKit.newKey("resume");
        kit.setting(key, "chunk_size", 3);
        ItemJob job = threeStepJob(key);
        List<Long> select = kit.items(job.groupOf("select"), 2);
        List<Long> process = kit.items(job.groupOf("process"), 10);
        List<Long> notify = kit.items(job.groupOf("notify"), 2);
        job.failReadAt.put("process", 3);
        BatchRunner runner = kit.runner(job);
        BatchParameters parameters = BatchParameters.of(Map.of("baseDate", "2026-10-01"));

        RunReport first = runner.runRequested(request(job, BatchRequestKind.NOW, parameters, null));
        assertThat(first.status()).isEqualTo(BatchRunStatus.FAIL);
        assertThat(kit.step(first.runId(), "select").get("status")).isEqualTo("OK");
        assertThat(kit.step(first.runId(), "process").get("status")).isEqualTo("FAIL");
        assertThat(kit.step(first.runId(), "notify").get("status")).as("닿지 못한 단계").isEqualTo("PENDING");
        String checkpoint = (String) kit.step(first.runId(), "process").get("checkpoint");
        assertThat(checkpoint).isEqualTo(String.valueOf(process.get(5)));

        job.failReadAt.clear();
        job.readAfterKeys.clear();
        RunReport resumed = runner.runRequested(request(job, BatchRequestKind.RESUME, BatchParameters.empty(), first.runId()));

        assertThat(resumed.status()).isEqualTo(BatchRunStatus.OK);
        assertThat(kit.step(resumed.runId(), "select").get("status")).as("원래 실행이 끝낸 단계").isEqualTo("SKIP");
        assertThat(job.readAfterKeys.get("select")).as("끝난 단계는 읽지도 않는다").isNull();
        assertThat(job.readAfterKeys.get("process").get(0)).as("실패한 단계는 체크포인트 다음부터").isEqualTo(checkpoint);
        assertThat(kit.step(resumed.runId(), "notify").get("status")).isEqualTo("OK");
        assertThat(job.seenParameters.get(1)).as("원래 실행의 파라미터를 이어 쓴다").isEqualTo(parameters);
        for (Long item : process) {
            assertThat(kit.touched(item)).as("두 실행을 합쳐 한 번씩").isEqualTo(1);
        }
        assertThat(kit.touched(select.get(0))).isEqualTo(1);
        assertThat(kit.touched(notify.get(0))).isEqualTo(1);
        Map<String, Object> run = kit.run(resumed.runId());
        assertThat(run.get("run_mode")).isEqualTo("RESUME");
        assertThat(run.get("resumed_from_run_id")).isEqualTo(first.runId());
        assertThat(run.get("params")).isEqualTo("{\"baseDate\":\"2026-10-01\"}");
    }

    @Test
    void 한_실행은_한_번만_이어_받는다_이어_받은_실행이_또_실패하면_그_실행에서_이어_받는다() {
        String key = BatchTestKit.newKey("once");
        kit.setting(key, "chunk_size", 2);
        ItemJob job = threeStepJob(key);
        kit.items(job.groupOf("select"), 1);
        kit.items(job.groupOf("process"), 10);
        kit.items(job.groupOf("notify"), 1);
        BatchRunner runner = kit.runner(job);
        job.failReadAt.put("process", 2);
        RunReport first = runner.runRequested(request(job, BatchRequestKind.NOW, BatchParameters.empty(), null));
        assertThat(first.status()).isEqualTo(BatchRunStatus.FAIL);

        // 이어 받은 실행도 실패한다(읽기 횟수는 새 실행에서 다시 센다 — 두 번째 읽기에서)
        RunReport second = runner.runRequested(request(job, BatchRequestKind.RESUME, BatchParameters.empty(), first.runId()));
        assertThat(second.status()).isEqualTo(BatchRunStatus.FAIL);

        RunReport again = runner.runRequested(request(job, BatchRequestKind.RESUME, BatchParameters.empty(), first.runId()));
        assertThat(again.outcome()).as("이미 이어 받은 실행은 다시 이어 받지 않는다").isEqualTo(Outcome.REJECTED);
        assertThat(again.rejectKey()).isEqualTo(BatchRunner.NOT_RESUMABLE_KEY);
        assertThat(kit.count("SELECT count(*) FROM batch_runs WHERE resumed_from_run_id = ? AND run_mode = 'RESUME'", first.runId())).isEqualTo(1);

        // DB 도 막는다(부분 유일 색인)
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO batch_runs (id, job_key, status, trigger_type, started_at, finished_at, source, run_mode, resumed_from_run_id) "
                + "VALUES (?, ?, 'FAIL', 'MANUAL', now(), now(), 'JAVA', 'RESUME', ?)", kit.ids.nextId(), key, first.runId())).isInstanceOf(DuplicateKeyException.class);

        job.failReadAt.clear();
        RunReport third = runner.runRequested(request(job, BatchRequestKind.RESUME, BatchParameters.empty(), second.runId()));
        assertThat(third.status()).as("또 실패한 이어 받은 실행에서는 다시 이어 받는다").isEqualTo(BatchRunStatus.OK);
        assertThat(kit.run(third.runId()).get("resumed_from_run_id")).isEqualTo(second.runId());
    }

    @Test
    void 실패로_끝나지_않았거나_체크포인트가_없거나_작업이_지원하지_않으면_이어_받지_않는다() {
        String key = BatchTestKit.newKey("noresume");
        ItemJob job = kit.singleStepJob(key); // resumable = false
        kit.items(job.groupOf("main"), 2);
        BatchRunner runner = kit.runner(job);
        RunReport ok = runner.runRequested(BatchTestKit.now(job.definition));

        RunReport rejected = runner.runRequested(request(job, BatchRequestKind.RESUME, BatchParameters.empty(), ok.runId()));

        assertThat(rejected.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(rejected.rejectKey()).isEqualTo(BatchRunner.NOT_RESUMABLE_KEY);
    }

    @Test
    void 실패_건만_다시는_미해결_건만_처리하고_성공하면_해결_또_실패하면_원래_줄은_시도_더하기_1_새_실행에_새_줄() {
        String key = BatchTestKit.newKey("retry");
        kit.setting(key, "chunk_size", 5);
        ItemJob job = kit.singleStepJob(key);
        List<Long> items = kit.items(job.groupOf("main"), 6);
        long fixable = items.get(1);
        long stubborn = items.get(4);
        job.poison.addAll(List.of(fixable, stubborn));
        BatchRunner runner = kit.runner(job);

        RunReport first = runner.runRequested(BatchTestKit.now(job.definition));
        assertThat(first.status()).isEqualTo(BatchRunStatus.OK);
        assertThat(kit.failuresOf(first.runId())).hasSize(2);

        job.poison.remove(fixable);
        job.readAfterKeys.clear();
        RunReport retry = runner.runRequested(request(job, BatchRequestKind.RETRY_FAILED, BatchParameters.empty(), first.runId()));

        assertThat(job.readAfterKeys.get("main")).as("덩어리 읽기(next)를 하지 않는다 — 실패 건만 byIds 로").isNull();
        assertThat(kit.touched(fixable)).isEqualTo(1);
        assertThat(kit.touched(items.get(0))).as("이미 성공한 건은 다시 처리하지 않는다").isEqualTo(1);
        List<Map<String, Object>> original = kit.failuresOf(first.runId());
        Map<String, Object> fixed = original.stream().filter(row -> row.get("target_id").equals(String.valueOf(fixable))).findFirst().orElseThrow();
        Map<String, Object> still = original.stream().filter(row -> row.get("target_id").equals(String.valueOf(stubborn))).findFirst().orElseThrow();
        assertThat(fixed.get("resolved")).isEqualTo(true);
        assertThat(fixed.get("resolved_by_run_id")).isEqualTo(retry.runId());
        assertThat(still.get("resolved")).isEqualTo(false);
        assertThat(still.get("attempts")).as("원래 줄은 미해결인 채 시도 +1").isEqualTo(2);
        List<Map<String, Object>> fresh = kit.failuresOf(retry.runId());
        assertThat(fresh).hasSize(1);
        assertThat(fresh.get(0).get("target_id")).isEqualTo(String.valueOf(stubborn));
        assertThat(fresh.get(0).get("attempts")).as("새 실행의 새 줄 = 원래 + 1").isEqualTo(2);
        Map<String, Object> run = kit.run(retry.runId());
        assertThat(run.get("run_mode")).isEqualTo("RETRY_FAILED");
        assertThat(run.get("resumed_from_run_id")).isEqualTo(first.runId());
        assertThat(run.get("read_count")).isEqualTo(2L);
        assertThat(run.get("affected_count")).isEqualTo(1L);
        assertThat(run.get("failed_count")).isEqualTo(1L);

        job.poison.clear();
        RunReport again = runner.runRequested(request(job, BatchRequestKind.RETRY_FAILED, BatchParameters.empty(), first.runId()));
        assertThat(again.status()).isEqualTo(BatchRunStatus.OK);
        assertThat(kit.count("SELECT count(*) FROM batch_run_failures WHERE run_id = ? AND NOT resolved", first.runId())).isZero();
        RunReport nothing = runner.runRequested(request(job, BatchRequestKind.RETRY_FAILED, BatchParameters.empty(), first.runId()));
        assertThat(nothing.outcome()).as("미해결 실패가 없으면 돌지 않는다").isEqualTo(Outcome.REJECTED);
        assertThat(nothing.rejectKey()).isEqualTo(BatchRunner.NO_FAILURES_KEY);
    }
}
