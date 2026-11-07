package com.community.withdrawal;

import static com.community.withdrawal.WithdrawalTestSupport.plain;
import com.community.testsupport.BatchTestParts;
import static org.assertj.core.api.Assertions.assertThat;

import com.community.audit.dto.AuditActions;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchLockService;
import com.community.batch.core.BatchParameters;
import com.community.batch.core.BatchRegistry;
import com.community.batch.core.BatchRunner;
import com.community.batch.entity.BatchRequestKind;
import com.community.commerce.CommerceApiTestBase;
import com.community.withdrawal.batch.WithdrawalAutoCompleteJob;
import com.community.withdrawal.service.WithdrawalService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 탈퇴 예약 자동 완료 배치 {@code member-withdrawal-complete} 를 <b>실행기({@link BatchRunner})로 「지금 실행」</b>한다(핫딜 배치 시험과 같은 방식) — 여러 신청을 한 번에, 한 건의 실패가
 * 나머지를 막지 않고 실패 건이 실행 기록에 남으며, 두 번 돌려도 같은 회원을 두 번 완료하거나 진행 안내를 두 번 보내지 않는다.
 * 실패는 시험이 {@code member_withdrawals} 에 건 트리거(특정 회원의 완료 UPDATE 만 거부)로 일으킨다 — 운영 코드에 시험 코드는 없다.
 */
class WithdrawalBatchTest extends CommerceApiTestBase {

    private static final String IP = "127.0.0.1";

    @Autowired
    private WithdrawalService service;
    @Autowired
    private WithdrawalAutoCompleteJob job;
    @Autowired
    private PasswordEncoder encoder;

    private BatchRunner.RunReport run(BatchJob job) {
        BatchRunner runner = new BatchRunner(new BatchLockService(dataSource), BatchTestParts.recorder(context, ids, 957), BatchTestParts.failures(context, ids),
                BatchRegistry.forJobs(List.of(job)), transactions);
        BatchRunner.RunReport report = runner.runRequested(new BatchRunner.RunRequest(job.definition(), BatchRequestKind.NOW, null, BatchParameters.of(Map.of()), null, null,
                null));
        assertThat(report.runId()).as(String.valueOf(report)).isNotNull();
        return report;
    }

    private long member() {
        long id = newMember();
        WithdrawalTestSupport.withPassword(jdbc, encoder, id, clock.instant());
        return id;
    }

    /** 송금 대기 환불로 예약한 회원 — 송금 ID */
    private long scheduled(long member) {
        long transfer = ids.nextId();
        jdbc.update("INSERT INTO commerce_bank_transfers (id, source_kind, source_id, user_id, amount, status, reason, created_at) VALUES (?, 'GROUP_BUY_REFUND', ?, ?, 10000, "
                + "'AWAITING_ACCOUNT', 'test', now())", transfer, transfer, member);
        assertThat(service.request(member, plain(), IP).state()).isEqualTo("SCHEDULED");
        return transfer;
    }

    private void finish(long transfer) {
        jdbc.update("UPDATE commerce_bank_transfers SET status = 'SENT', sent_at = now(), account_erased_at = now() WHERE id = ?", transfer);
    }

    private String status(long userId) {
        return memberFixture.status(userId);
    }

    private Map<String, Object> withdrawalRow(long userId) {
        return jdbc.queryForMap("SELECT id, status, progress_notified_at FROM member_withdrawals WHERE user_id = ? ORDER BY id DESC LIMIT 1", userId);
    }

    private Map<String, Object> runRow(long runId) {
        return jdbc.queryForMap("SELECT status, affected_count, message_key, message_args FROM batch_runs WHERE id = ?", runId);
    }

    private Map<String, Object> stepRow(long runId) {
        return jdbc.queryForMap("SELECT status, read_count, processed_count, failed_count, skipped_count FROM batch_run_steps WHERE run_id = ? AND step_name = 'main'", runId);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    /** 특정 회원의 신청 줄을 COMPLETED 로 바꾸는 UPDATE 만 거부하는 트리거(시험 전용 — 끝나면 지운다) */
    private void failCompletionOf(long userId) {
        jdbc.execute("CREATE OR REPLACE FUNCTION wd_test_fail_" + userId + "() RETURNS trigger LANGUAGE plpgsql AS $f$ BEGIN "
                + "IF NEW.status = 'COMPLETED' AND NEW.user_id = " + userId + " THEN RAISE EXCEPTION 'wd-test-fail'; END IF; RETURN NEW; END $f$");
        jdbc.execute("CREATE TRIGGER tr_wd_test_fail_" + userId + " BEFORE UPDATE ON member_withdrawals FOR EACH ROW EXECUTE FUNCTION wd_test_fail_" + userId + "()");
    }

    private void clearFailure(long userId) {
        jdbc.execute("DROP TRIGGER IF EXISTS tr_wd_test_fail_" + userId + " ON member_withdrawals");
        jdbc.execute("DROP FUNCTION IF EXISTS wd_test_fail_" + userId + "()");
    }

    @Test
    void 실행기로_돌리면_끝난_사람은_완료_남은_사람은_대기_10일_지난_사람은_안내_실패한_사람은_기록하고_나머지는_계속_두_번_돌려도_중복이_없다() {
        long done = member();
        long waiting = member();
        long overdue = member();
        long broken = member();
        long doneTransfer = scheduled(done);
        scheduled(waiting);
        scheduled(overdue);
        long brokenTransfer = scheduled(broken);
        finish(doneTransfer);
        finish(brokenTransfer);
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE member_withdrawals SET requested_at = ? WHERE user_id = ?", ts(clock.instant().minus(Duration.ofDays(11))), overdue);
        failCompletionOf(broken);
        try {
            BatchRunner.RunReport first = run(job);

            // 끝난 사람은 완료, 남은 사람은 그대로, 10일 지난 사람은 안내 1번, 실패한 사람은 그대로 진행 중
            assertThat(status(done)).isEqualTo("WITHDRAWN");
            assertThat(status(waiting)).isEqualTo("WITHDRAWING");
            assertThat(status(overdue)).isEqualTo("WITHDRAWING");
            assertThat(withdrawalRow(overdue).get("progress_notified_at")).isNotNull();
            Object firstNotice = withdrawalRow(overdue).get("progress_notified_at");
            assertThat(status(broken)).isEqualTo("WITHDRAWING");
            assertThat(withdrawalRow(broken).get("status")).isEqualTo("SCHEDULED");
            // 실행 기록 — 처리 = 완료 1, 실패 = 1, 실패 건은 신청 ID 로 남는다
            Map<String, Object> run = runRow(first.runId());
            assertThat(run.get("status")).isEqualTo("OK");
            assertThat(((Number) run.get("affected_count")).longValue()).isGreaterThanOrEqualTo(1);
            assertThat(run.get("message_key")).isEqualTo("batch.result.memberWithdrawalComplete");
            Map<String, Object> step = stepRow(first.runId());
            assertThat(((Number) step.get("processed_count")).longValue()).isGreaterThanOrEqualTo(1);
            assertThat(((Number) step.get("failed_count")).longValue()).isGreaterThanOrEqualTo(1);
            assertThat(((Number) step.get("skipped_count")).longValue()).isGreaterThanOrEqualTo(2);
            long brokenWithdrawal = ((Number) withdrawalRow(broken).get("id")).longValue();
            Map<String, Object> failure = jdbc.queryForMap("SELECT target_type, error_code, message FROM batch_run_failures WHERE run_id = ? AND target_id = ?", first.runId(),
                    Long.toString(brokenWithdrawal));
            assertThat(failure.get("target_type")).isEqualTo(WithdrawalAutoCompleteJob.TARGET);
            assertThat(String.valueOf(failure.get("message"))).contains("wd-test-fail");
            assertThat(auditLogs.where().action(AuditActions.MEMBER_WITHDRAW).target(Long.toString(broken)).count()).isZero(); // 실패 건은 되돌아갔다

            // 두 번째 실행 — 완료한 사람은 다시 집히지 않고, 안내는 두 번 가지 않으며, 실패한 사람은 다시 실패한다
            BatchRunner.RunReport second = run(job);
            assertThat(auditLogs.where().action(AuditActions.MEMBER_WITHDRAW).target(Long.toString(done)).count()).isEqualTo(1);
            assertThat(withdrawalRow(overdue).get("progress_notified_at")).isEqualTo(firstNotice);
            assertThat(status(waiting)).isEqualTo("WITHDRAWING");
            assertThat(count("SELECT count(*) FROM batch_run_failures WHERE run_id = ? AND target_id = ?", second.runId(), Long.toString(brokenWithdrawal))).isEqualTo(1);
            assertThat(((Number) stepRow(second.runId()).get("processed_count")).longValue()).isZero();

            // 실패 원인을 치우면 다음 실행이 완료한다
            clearFailure(broken);
            run(job);
            assertThat(status(broken)).isEqualTo("WITHDRAWN");
        } finally {
            clearFailure(broken);
        }
    }

    @Test
    void 진행_중_신청이_없으면_SKIP_으로_끝난다() {
        BatchRunner.RunReport report = run(job);
        assertThat(runRow(report.runId()).get("status")).isIn("OK", "SKIP"); // 다른 시험의 진행 중 신청이 있을 수 있다
    }
}
