package com.community.point;

import static com.community.point.PointBatchTestSupport.number;
import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchRunner.RunReport;
import com.community.batch.entity.BatchRequestKind;
import com.community.batch.entity.BatchRunStatus;
import com.community.point.batch.PointExpireJob;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 포인트 소멸 배치 {@code point-expire}(commerce.md 15-3·15-5, 계약 「1-1」) — 실제 PostgreSQL 위에서 배치 core 로 돌린다. 대상은 파라미터 {@code userId} 로 시험 회원에 묶는다.
 *
 * <p>덩어리 실패를 일부러 내려고 시험 전용 「독 묶음」 표와 트리거를 둔다: 그 표에 든 묶음에 반영 줄({@code point_ledger_lots})을 넣으면 오류가 난다 → 그 묶음의 소멸 트랜잭션이 통째로 되돌려진다.
 */
class PointExpireJobTest extends PointTestBase {

    private static final String KEY = BatchCatalog.POINT_EXPIRE.key();

    private static PointBatchTestSupport batch;
    private static PointExpireJob job;

    @BeforeAll
    static void setUpBatch() {
        batch = new PointBatchTestSupport(kit);
        job = new PointExpireJob(batch.queries, kit.points, batch.clocks);
        kit.jdbc.execute("CREATE TABLE IF NOT EXISTS zz_point_poison_lots (lot_id bigint PRIMARY KEY)");
        kit.jdbc.execute("CREATE OR REPLACE FUNCTION zz_point_poison() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                + "IF EXISTS (SELECT 1 FROM zz_point_poison_lots WHERE lot_id = NEW.lot_id) THEN RAISE EXCEPTION 'poisoned lot (test)'; END IF; RETURN NEW; END $$");
        kit.jdbc.execute("DROP TRIGGER IF EXISTS zz_point_poison ON point_ledger_lots");
        kit.jdbc.execute("CREATE TRIGGER zz_point_poison BEFORE INSERT ON point_ledger_lots FOR EACH ROW EXECUTE FUNCTION zz_point_poison()");
    }

    @AfterAll
    static void tearDownBatch() {
        kit.jdbc.execute("DROP TRIGGER IF EXISTS zz_point_poison ON point_ledger_lots");
        kit.jdbc.execute("DELETE FROM zz_point_poison_lots");
        batch.clearSetting(KEY, "chunk_size");
        batch.clearSetting(KEY, "failure_limit");
    }

    private static void poison(long... lots) {
        for (long lot : lots) {
            kit.jdbc.update("INSERT INTO zz_point_poison_lots (lot_id) VALUES (?)", lot);
        }
    }

    private static void cure(long... lots) {
        for (long lot : lots) {
            kit.jdbc.update("DELETE FROM zz_point_poison_lots WHERE lot_id = ?", lot);
        }
    }

    private static int expireLines(long userId) {
        return kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND kind = 'EXPIRE'", userId);
    }

    @Test
    void 소멸일이_지난_무상_묶음만_소멸하고_유상과_아직인_묶음은_그대로_두_번_돌려도_같다() {
        long m = kit.newMember();
        Instant t0 = kit.clock.instant();
        long old = kit.lotOf(kit.grantLine(m, 100)); // 소멸 = t0 + 365일
        long paid = kit.chargePaid(m, 50);
        kit.clock.advance(Duration.ofDays(200));
        long fresh = kit.lotOf(kit.grantLine(m, 30)); // 소멸 = t0 + 565일
        kit.clock.set(t0.plus(Duration.ofDays(365))); // 소멸일 = 지금(경계 — 소멸일 ≤ 지금이면 소멸)

        RunReport first = batch.runFor(job, m);

        assertThat(first.status()).isEqualTo(BatchRunStatus.OK);
        Map<String, Object> step = batch.step(first.runId());
        assertThat(number(step.get("read_count"))).as("유상·아직인 묶음은 고르지도 않는다").isEqualTo(1);
        assertThat(number(step.get("processed_count"))).isEqualTo(1);
        assertThat(kit.remaining(old)).isZero();
        assertThat(kit.remaining(fresh)).isEqualTo(30);
        assertThat(kit.remaining(paid)).as("유상은 소멸하지 않는다(D12)").isEqualTo(50);
        assertThat(kit.free(m)).isEqualTo(30);
        assertThat(kit.paid(m)).isEqualTo(50);
        Map<String, Object> line = kit.jdbc.queryForMap("SELECT reason, delta, free_delta, paid_delta, ref_type, ref_id FROM point_ledger WHERE user_id = ? AND kind = 'EXPIRE'", m);
        assertThat(line).containsEntry("reason", "EXPIRE").containsEntry("delta", -100).containsEntry("free_delta", -100L).containsEntry("paid_delta", 0L)
                .containsEntry("ref_type", "LOT").containsEntry("ref_id", old);
        assertThat(batch.run(first.runId()).get("message_key")).isEqualTo("batch.result.pointExpire");
        kit.assertInvariants(m);

        RunReport again = batch.runFor(job, m);

        assertThat(again.status()).as("멱등 — 할 일이 없으면 건너뜀").isEqualTo(BatchRunStatus.SKIP);
        assertThat(number(batch.step(again.runId()).get("read_count"))).isZero();
        assertThat(batch.run(again.runId()).get("message_key")).isEqualTo("batch.result.pointExpire.none");
        assertThat(expireLines(m)).as("두 번 소멸하지 않는다").isEqualTo(1);
        assertThat(kit.free(m)).isEqualTo(30);
        kit.assertInvariants(m);
    }

    @Test
    void 하루를_놓쳐도_지난_것을_모두_소멸하고_이미_서비스가_소멸한_묶음은_건너뛴다() {
        long m = kit.newMember();
        Instant t0 = kit.clock.instant();
        List<Long> lots = new ArrayList<>();
        for (int day = 0; day < 3; day++) {
            kit.clock.set(t0.plus(Duration.ofDays(day)));
            lots.add(kit.lotOf(kit.grantLine(m, 10 + day)));
        }
        kit.clock.set(t0.plus(Duration.ofDays(400))); // 사흘 치가 모두 지났다(배치가 며칠 돌지 않았다)
        assertThat(kit.expireLot(lots.get(0))).as("사용 전 소멸처럼 서비스가 먼저 소멸").isEqualTo(10);

        RunReport report = batch.runFor(job, m);

        assertThat(report.status()).isEqualTo(BatchRunStatus.OK);
        Map<String, Object> step = batch.step(report.runId());
        assertThat(number(step.get("read_count"))).as("남은 양이 0 인 묶음은 대상이 아니다").isEqualTo(2);
        assertThat(number(step.get("processed_count"))).isEqualTo(2);
        lots.forEach(lot -> assertThat(kit.remaining(lot)).isZero());
        assertThat(expireLines(m)).isEqualTo(3);
        assertThat(kit.free(m)).isZero();
        kit.assertInvariants(m);
    }

    @Test
    void 동결_회원의_지난_묶음도_소멸한다() {
        long m = kit.newMember();
        Instant t0 = kit.clock.instant();
        long lot = kit.lotOf(kit.grantLine(m, 40));
        kit.freeze(m);
        kit.clock.set(t0.plus(Duration.ofDays(366)));

        RunReport report = batch.runFor(job, m);

        assertThat(report.status()).isEqualTo(BatchRunStatus.OK);
        assertThat(kit.remaining(lot)).isZero();
        assertThat(kit.free(m)).isZero();
        kit.assertInvariants(m);
    }

    @Test
    void 덩어리가_실패하면_그_묶음만_실패로_남고_나머지는_소멸한다() {
        long m = kit.newMember();
        Instant t0 = kit.clock.instant();
        List<Long> lots = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            kit.clock.set(t0.plusSeconds(i));
            lots.add(kit.lotOf(kit.grantLine(m, 10)));
        }
        kit.clock.set(t0.plus(Duration.ofDays(366)));
        long bad = lots.get(1);
        poison(bad);
        try {
            RunReport report = batch.runFor(job, m);

            assertThat(report.status()).as("한 건 실패가 전체를 멈추지 않는다").isEqualTo(BatchRunStatus.OK);
            assertThat(kit.remaining(lots.get(0))).isZero();
            assertThat(kit.remaining(bad)).as("실패한 묶음의 소멸은 통째로 되돌려졌다").isEqualTo(10);
            assertThat(kit.remaining(lots.get(2))).isZero();
            List<Map<String, Object>> failed = batch.failures(report.runId());
            assertThat(failed).hasSize(1);
            assertThat(failed.get(0)).containsEntry("target_type", PointExpireJob.TARGET).containsEntry("target_id", Long.toString(bad))
                    .containsEntry("step_name", "main");
            assertThat(number(batch.step(report.runId()).get("failed_count"))).isEqualTo(1);
            kit.assertInvariants(m);
        } finally {
            cure(bad);
        }
    }

    @Test
    void 실패_지점부터_다시는_체크포인트_다음부터_읽고_실패_건만_다시는_실패한_묶음만_처리한다() {
        long m = kit.newMember();
        Instant t0 = kit.clock.instant();
        List<Long> lots = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            kit.clock.set(t0.plusSeconds(i)); // 소멸일이 1초씩 달라 순서가 정해진다
            lots.add(kit.lotOf(kit.grantLine(m, 10)));
        }
        kit.clock.set(t0.plus(Duration.ofDays(366)));
        batch.setting(KEY, "chunk_size", 2);
        batch.setting(KEY, "failure_limit", 1);
        long first = lots.get(0);
        long third = lots.get(2);
        poison(first, third);
        try {
            // 덩어리 1 [1,2]: 1 실패(한도 안) · 2 소멸 → 확정·체크포인트 = 2. 덩어리 2 [3,4]: 3 실패 → 한도(1) 초과로 실행 FAIL
            RunReport failedRun = batch.runFor(job, m);

            assertThat(failedRun.status()).isEqualTo(BatchRunStatus.FAIL);
            assertThat(kit.remaining(lots.get(1))).isZero();
            for (int i : new int[] {0, 2, 3, 4}) {
                assertThat(kit.remaining(lots.get(i))).as("묶음 " + (i + 1)).isEqualTo(10);
            }
            String checkpoint = (String) batch.step(failedRun.runId()).get("checkpoint");
            assertThat(checkpoint).as("체크포인트 = 확정된 덩어리의 마지막 묶음").endsWith(":" + lots.get(1));
            assertThat(batch.failures(failedRun.runId())).extracting(row -> row.get("target_id")).containsExactly(Long.toString(first), Long.toString(third));

            cure(first, third);
            RunReport resumed = batch.rerun(job, BatchRequestKind.RESUME, failedRun.runId());

            assertThat(resumed.status()).isEqualTo(BatchRunStatus.OK);
            assertThat(batch.run(resumed.runId()).get("resumed_from_run_id")).isEqualTo(failedRun.runId());
            assertThat(number(batch.step(resumed.runId()).get("processed_count"))).isEqualTo(3);
            for (int i : new int[] {2, 3, 4}) {
                assertThat(kit.remaining(lots.get(i))).as("체크포인트 뒤 묶음 " + (i + 1)).isZero();
            }
            assertThat(kit.remaining(first)).as("체크포인트 앞(실패 건)은 다시 읽지 않는다").isEqualTo(10);

            RunReport retried = batch.rerun(job, BatchRequestKind.RETRY_FAILED, failedRun.runId());

            assertThat(retried.status()).isEqualTo(BatchRunStatus.OK);
            Map<String, Object> retryStep = batch.step(retried.runId());
            assertThat(number(retryStep.get("processed_count"))).as("묶음 1").isEqualTo(1);
            assertThat(number(retryStep.get("skipped_count"))).as("묶음 3 은 이미 소멸해 대상이 아니다").isEqualTo(1);
            assertThat(kit.remaining(first)).isZero();
            assertThat(kit.count("SELECT count(*) FROM batch_run_failures WHERE run_id = ? AND NOT resolved", failedRun.runId())).isZero();
            assertThat(expireLines(m)).isEqualTo(5);
            assertThat(kit.free(m)).isZero();
            kit.assertInvariants(m);
        } finally {
            cure(first, third);
            batch.clearSetting(KEY, "chunk_size");
            batch.clearSetting(KEY, "failure_limit");
        }
    }
}
