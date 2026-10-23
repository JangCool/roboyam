package com.community.batch.core;

import static com.community.batch.core.BatchJdbc.ts;

import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 건별 실패({@code batch_run_failures}) 기록 — <b>실패한 건만</b> 남긴다(성공 건은 단계 건수로만 센다). 실패 줄은 덩어리 일과 <b>다른 트랜잭션</b>으로 넣는다 — 그 건의 일이 롤백돼도
 * 실패했다는 기록은 남아야 한다. 문구는 부르는 쪽이 {@link BatchErrorText} 로 가려 넘기고 여기서 한 번 더 가린다(500자). Native SQL 인 이유는 {@link BatchJdbc}.
 */
@Component
public class BatchFailureRecorder {

    /** 아직 해결하지 않은 실패 한 줄 */
    public record OpenFailure(long id, String stepName, String targetType, String targetId, String errorCode, int attempts) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate requiresNew;
    private final SnowflakeIdGenerator ids;

    public BatchFailureRecorder(DataSource dataSource, PlatformTransactionManager transactions, SnowflakeIdGenerator ids) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.requiresNew = BatchJdbc.requiresNew(transactions);
        this.ids = ids;
    }

    /** 실패 한 줄을 넣는다(따로 확정). 돌려주는 값은 줄 ID */
    public long record(long runId, String step, String targetType, String targetId, String errorCode, String message, int attempts) {
        long id = ids.nextId();
        String safeTarget = targetId == null ? "" : (targetId.length() <= 100 ? targetId : targetId.substring(0, 100));
        String safeType = targetType == null || targetType.isBlank() ? "UNKNOWN" : (targetType.length() <= 40 ? targetType : targetType.substring(0, 40));
        requiresNew.executeWithoutResult(status -> jdbc.update(
                "INSERT INTO batch_run_failures (id, run_id, step_name, target_type, target_id, error_code, message, attempts, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, runId, step, safeType, safeTarget, errorCode, BatchErrorText.mask(message == null ? "" : message), attempts, ts(BatchJdbc.now())));
        return id;
    }

    /** 그 실행·단계의 미해결 실패(오래된 것부터) */
    public List<OpenFailure> unresolved(long runId, String step) {
        return jdbc.query("SELECT id, step_name, target_type, target_id, error_code, attempts FROM batch_run_failures WHERE run_id = ? AND step_name = ? AND NOT resolved ORDER BY id",
                (rs, row) -> new OpenFailure(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6)), runId, step);
    }

    /** 그 실행의 단계별 미해결 실패 수(없는 단계는 빠진다) */
    public Map<String, Long> unresolvedCountByStep(long runId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT step_name, count(*) FROM batch_run_failures WHERE run_id = ? AND NOT resolved GROUP BY step_name", rs -> {
            counts.put(rs.getString(1), rs.getLong(2));
        }, runId);
        return counts;
    }

    /** 해결 표시 — <b>부르는 쪽 트랜잭션에 참여한다</b>(그 건을 다시 처리한 트랜잭션과 함께 확정되게) */
    public void resolve(long failureId, long byRunId) {
        jdbc.update("UPDATE batch_run_failures SET resolved = true, resolved_by_run_id = ?, resolved_at = ? WHERE id = ? AND NOT resolved", byRunId, ts(BatchJdbc.now()), failureId);
    }

    /** 다시 해도 실패 — 원래 줄은 미해결인 채 시도 횟수만 +1(따로 확정, 확정 규칙 4) */
    public void bumpAttempts(long failureId) {
        requiresNew.executeWithoutResult(status -> jdbc.update("UPDATE batch_run_failures SET attempts = attempts + 1 WHERE id = ? AND NOT resolved", failureId));
    }
}
