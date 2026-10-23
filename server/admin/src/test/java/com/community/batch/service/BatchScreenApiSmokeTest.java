package com.community.batch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.admin.AdminApplication;
import com.community.batch.core.BatchRequestExecutor;
import com.community.batch.core.BatchRequestQueue.ClaimedRequest;
import com.community.batch.dto.request.BatchFailureSearchRequest;
import com.community.batch.dto.request.BatchLogSearchRequest;
import com.community.batch.dto.request.BatchRunRequestBody;
import com.community.batch.dto.response.BatchFailurePage;
import com.community.batch.dto.response.BatchJobView;
import com.community.batch.dto.response.BatchLogView;
import com.community.batch.dto.response.BatchRunDetailView;
import com.community.batch.dto.response.BatchRunRequestView;
import com.community.batch.exception.BatchNoFailuresException;
import com.community.batch.exception.BatchNotResumableException;
import com.community.batch.exception.BatchRequestNotFoundException;
import com.community.batch.exception.BatchRunningException;
import com.community.batch.exception.BatchUnavailableException;
import com.community.permission.dto.AdminActor;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 배치 화면 API 의 서버 쪽(docs/features/batch-contract.md 「0-2 보강」)을 관리자 앱 문맥 그대로(JPA 매핑·QueryDSL·메시지 번들) 한 바퀴 돌려 본다 — 지금 실행 요청 → 배치 서버가 집어 실행 →
 * 요청·목록·로그·상세·실패 건, 다시 실행의 409. 실제 PostgreSQL(커머스 시험 DB, pg_cron 없음). 이 서버는 배치를 끈 채(roboyam.batch.enabled=false) 띄우고 집기는 시험이 직접 한다.
 */
@SpringBootTest(classes = AdminApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class BatchScreenApiSmokeTest {

    private static final String JOB = "file-orphan-cleanup";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        CommerceTestDatabase.migrated("admin");
        registry.add("spring.datasource.url", CommerceTestDatabase::jdbcUrl);
        registry.add("spring.datasource.username", CommerceTestDatabase::username);
        registry.add("spring.datasource.password", CommerceTestDatabase::password);
        registry.add("roboyam.batch.enabled", () -> "false");
        registry.add("roboyam.notification.watcher-enabled", () -> "false");
    }

    @Autowired
    private BatchAdminService service;

    @Autowired
    private BatchQueryService queries;

    @Autowired
    private BatchRequestExecutor executor;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SnowflakeIdGenerator ids;

    private AdminActor operator() {
        long id = ids.nextId();
        String name = "배치" + (id % 100_000_000L);
        jdbc.update("INSERT INTO operators (id, email, operator_name, password_hash, is_root, approved) VALUES (?, ?, ?, 'x', false, true)", id, "batch" + id + "@test.invalid", name);
        return new AdminActor(id, name, "batch" + id + "@test.invalid", true, Set.of(), "127.0.0.1");
    }

    @Test
    void 지금_실행_요청부터_실행_상세까지_한_바퀴() {
        AdminActor actor = operator();

        BatchRunRequestView requested = service.requestRun(actor, JOB, null);
        assertThat(requested.status()).isEqualTo("PENDING");
        assertThat(requested.kind()).isEqualTo("NOW");
        assertThat(requested.operatorName()).isEqualTo(actor.operatorName());
        assertThat(requested.params()).isEmpty();
        assertThat(requested.steps()).isNull();
        assertThatThrownBy(() -> service.requestRun(actor, JOB, null)).as("끝나지 않은 요청이 있으면 409").isInstanceOf(BatchRunningException.class);
        assertThatThrownBy(() -> service.requestRun(actor, JOB, new BatchRunRequestBody(Map.of("x", "1"), null))).isInstanceOf(BatchRunningException.class);

        BatchJobView listed = queries.list().stream().filter(job -> job.jobName().equals(JOB)).findFirst().orElseThrow();
        assertThat(listed.pendingRequest()).isNotNull();
        assertThat(listed.pendingRequest().requestId()).isEqualTo(requested.requestId());
        assertThat(listed.steps()).hasSize(1);
        assertThat(listed.steps().get(0).name()).isEqualTo("main");
        assertThat(listed.steps().get(0).label()).isEqualTo("실행");
        assertThat(listed.params()).isEmpty();
        assertThat(listed.resumable()).isFalse();

        ClaimedRequest claimed = executor.claimNext().orElseThrow();
        assertThat(String.valueOf(claimed.id())).isEqualTo(requested.requestId());
        executor.execute(claimed);

        BatchRunRequestView done = queries.request(requested.requestId());
        assertThat(done.status()).isEqualTo("DONE");
        assertThat(done.runId()).isNotNull();
        assertThat(done.finishedAt()).isNotNull();

        BatchRunDetailView detail = queries.detail(done.runId());
        assertThat(detail.status()).isEqualTo("SKIP");
        assertThat(detail.runMode()).isEqualTo("NORMAL");
        assertThat(detail.requestId()).isEqualTo(requested.requestId());
        assertThat(detail.trigger()).isEqualTo("MANUAL");
        assertThat(detail.operatorName()).isEqualTo(actor.operatorName());
        assertThat(detail.steps()).hasSize(1);
        assertThat(detail.steps().get(0).status()).isEqualTo("OK");
        assertThat(detail.steps().get(0).processedCount()).isZero();
        assertThat(detail.unresolvedFailureCount()).isZero();
        assertThat(detail.canResume()).isFalse();
        assertThat(detail.canRetryFailures()).isFalse();
        assertThat(detail.summary()).isNotBlank();
        assertThat(detail.nodeId()).isNotNull();

        List<BatchLogView> logs = queries.logs(new BatchLogSearchRequest(JOB, null, null, null, null, null, null)).items();
        assertThat(logs).extracting(BatchLogView::logId).contains(done.runId());
        assertThat(logs.get(0).runMode()).isEqualTo("NORMAL");

        assertThatThrownBy(() -> service.requestResume(actor, done.runId())).isInstanceOf(BatchNotResumableException.class);
        assertThatThrownBy(() -> service.requestRetryFailures(actor, done.runId())).isInstanceOf(BatchNoFailuresException.class);
        BatchJobView after = queries.list().stream().filter(job -> job.jobName().equals(JOB)).findFirst().orElseThrow();
        assertThat(after.pendingRequest()).isNull();
        assertThat(after.lastRunId()).isEqualTo(done.runId());
    }

    @Test
    void 실패_건이_있는_실행의_상세와_실패_건만_다시_요청() {
        AdminActor actor = operator();
        long runId = ids.nextId();
        jdbc.update("INSERT INTO batch_runs (id, job_key, status, trigger_type, started_at, finished_at, source, run_mode, read_count, affected_count, failed_count, "
                + "skipped_count, checkpoint, params, error_message) VALUES (?, ?, 'FAIL', 'MANUAL', now() - interval '1 hour', now() - interval '50 minutes', 'JAVA', "
                + "'NORMAL', 5, 3, 2, 0, '42', '{\"memberId\":\"7\"}', 'IllegalStateException: 끊김')", runId, JOB);
        jdbc.update("INSERT INTO batch_run_steps (run_id, step_name, step_order, status, read_count, processed_count, failed_count, checkpoint) "
                + "VALUES (?, 'main', 1, 'FAIL', 5, 3, 2, '42')", runId);
        for (int i = 0; i < 2; i++) {
            jdbc.update("INSERT INTO batch_run_failures (id, run_id, step_name, target_type, target_id, error_code, message, occurred_at) "
                    + "VALUES (?, ?, 'main', 'FILE', ?, 'STORAGE_DELETE_FAILED', '저장소에서 지우지 못했다', now() - interval '55 minutes')", ids.nextId(), runId, "f" + i);
        }

        BatchRunDetailView detail = queries.detail(String.valueOf(runId));
        assertThat(detail.params()).containsEntry("memberId", "7");
        assertThat(detail.checkpoint()).isEqualTo("42");
        assertThat(detail.failedCount()).isEqualTo(2L);
        assertThat(detail.unresolvedFailureCount()).isEqualTo(2);
        assertThat(detail.canRetryFailures()).isTrue();
        assertThat(detail.canResume()).as("이 작업은 체크포인트 재시작을 지원하지 않는다").isFalse();
        assertThat(detail.errorMessage()).isEqualTo("IllegalStateException: 끊김");

        BatchFailurePage unresolved = queries.failures(String.valueOf(runId), new BatchFailureSearchRequest("false", "0", "10"));
        assertThat(unresolved.total()).isEqualTo(2);
        assertThat(unresolved.items()).allSatisfy(failure -> {
            assertThat(failure.stepLabel()).isEqualTo("실행");
            assertThat(failure.targetLabel()).as("번들에 없는 대상 종류는 코드 그대로").isEqualTo("FILE");
            assertThat(failure.attempts()).isEqualTo(1);
            assertThat(failure.resolved()).isFalse();
        });
        assertThat(queries.failures(String.valueOf(runId), new BatchFailureSearchRequest("true", null, null)).total()).isZero();
        assertThatThrownBy(() -> queries.failures(String.valueOf(runId), new BatchFailureSearchRequest("maybe", null, null))).isInstanceOf(ValidationException.class);

        BatchRunRequestView retry = service.requestRetryFailures(actor, String.valueOf(runId));
        assertThat(retry.kind()).isEqualTo("RETRY_FAILED");
        assertThat(retry.params()).containsEntry("memberId", "7");
        assertThat(queries.detail(String.valueOf(runId)).pendingRequest()).as("이 실행에 대한 다시 실행 요청").isNotNull();
        jdbc.update("UPDATE batch_run_requests SET status = 'REJECTED', started_at = now(), finished_at = now() WHERE id = ?", Long.parseLong(retry.requestId()));
    }

    @Test
    void 없는_요청_쓸_수_없는_DB_배치_틀린_파라미터() {
        AdminActor actor = operator();
        assertThatThrownBy(() -> queries.request("123")).isInstanceOf(BatchRequestNotFoundException.class);
        assertThatThrownBy(() -> queries.request("abc")).isInstanceOf(BatchRequestNotFoundException.class);
        assertThatThrownBy(() -> service.requestRun(actor, "grade-promotion", null)).as("pg_cron 이 없는 시험 DB").isInstanceOf(BatchUnavailableException.class);
        BatchJobView db = queries.list().stream().filter(job -> job.jobName().equals("grade-promotion")).findFirst().orElseThrow();
        assertThat(db.available()).isFalse();
        assertThat(db.steps()).hasSize(1);
        assertThat(db.resumable()).isFalse();
        assertThatThrownBy(() -> service.requestRun(actor, JOB, new BatchRunRequestBody(null, List.of("main")))).as("고를 수 없는 단계")
                .isInstanceOf(ValidationException.class);
    }
}
