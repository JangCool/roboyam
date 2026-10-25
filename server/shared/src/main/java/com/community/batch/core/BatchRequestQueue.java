package com.community.batch.core;

import static com.community.batch.core.BatchJdbc.nullableLong;
import static com.community.batch.core.BatchJdbc.ts;

import com.community.batch.core.BatchLockService.BatchLock;
import com.community.batch.entity.BatchRequestKind;
import com.community.batch.entity.BatchRequestStatus;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실행 요청 표({@code batch_run_requests}) — 화면의 「지금 실행」·「실패 지점부터 다시」·「실패 건만 다시」가 줄을 넣고({@link #enqueue}), 배치를 켠 서버가 집는다({@link #claimNext}).
 * 같은 서버든 다른 서버든 같은 방식이라 배치를 별도 서버로 떼어도 새 장치가 필요 없다(docs/domain/commerce.md 15-2, 결정 6·7).
 *
 * <ul>
 *   <li>같은 배치에 끝나지 않은(PENDING·RUNNING) 요청은 하나만 — 부분 유일 색인({@code ux_batch_run_requests_open})이 막고, 두 번째는 {@link #enqueue} 가 빈 값을 돌려준다.</li>
 *   <li>집기는 {@code FOR UPDATE SKIP LOCKED} — 서버 여러 대가 동시에 집어도 한 요청은 한 서버만 집는다. 집은 요청은 곧바로 {@code RUNNING}(집은 서버의 node_id)으로 확정한다.</li>
 *   <li>집은 서버가 죽어 {@code RUNNING} 으로 남은 요청은 {@link #sweepStale} 이 정리한다 — 실행이 이미 끝났으면 DONE, 실행이 없고 그 배치의 잠금이 비어 있으면 REJECTED.</li>
 * </ul>
 * Native SQL 인 이유는 {@link BatchJdbc}({@code SKIP LOCKED}·부분 유일 색인).
 */
@Component
public class BatchRequestQueue {

    private static final Logger log = LoggerFactory.getLogger(BatchRequestQueue.class);

    /** 집은 서버가 사라져 끝맺지 못한 요청의 거절 사유 */
    public static final String INTERRUPTED_KEY = "batch.request.rejected.interrupted";

    /** 집은 요청 하나 */
    public record ClaimedRequest(long id, String jobKey, BatchRequestKind kind, Map<String, String> params, List<String> steps, Long sourceRunId, Long operatorId) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate requiresNew;
    private final SnowflakeIdGenerator ids;
    private final int nodeId;

    @Autowired
    public BatchRequestQueue(DataSource dataSource, PlatformTransactionManager transactions, SnowflakeIdGenerator ids, @Value("${roboyam.node-id}") int nodeId) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.requiresNew = BatchJdbc.requiresNew(transactions);
        this.ids = ids;
        this.nodeId = nodeId;
    }

    /**
     * 요청 한 줄을 넣는다 — <b>부르는 쪽 트랜잭션에 참여한다</b>(감사 기록과 함께 확정). 같은 배치에 끝나지 않은 요청이 이미 있으면 빈 값(부르는 쪽이 409 로 답한다 —
     * PostgreSQL 은 실패한 문장 뒤 그 트랜잭션을 못 쓰므로 부르는 쪽은 곧바로 예외를 던져 롤백해야 한다).
     */
    public Optional<Long> enqueue(String jobKey, BatchRequestKind kind, Map<String, String> params, List<String> steps, Long sourceRunId, Long operatorId) {
        long id = ids.nextId();
        try {
            jdbc.update("INSERT INTO batch_run_requests (id, job_key, kind, status, params, steps, source_run_id, operator_id, requested_at) "
                            + "VALUES (?, ?, ?, 'PENDING', ?, ?, ?, ?, ?)",
                    id, jobKey, kind.name(), BatchMessageCodec.encodeParams(params), BatchMessageCodec.encodeSteps(steps), sourceRunId, operatorId, ts(BatchJdbc.now()));
            return Optional.of(id);
        } catch (DuplicateKeyException e) {
            return Optional.empty();
        }
    }

    /**
     * 끝나지 않은 요청이 없을 때만 「지금 실행」 요청 한 줄을 넣는다(파라미터·단계 없음) — <b>부르는 쪽 트랜잭션에 참여하고, 이미 있으면 실패 없이 빈 값</b>
     * ({@code ON CONFLICT DO NOTHING} 이라 트랜잭션을 망가뜨리지 않는다). 업무 저장 뒤 「바로 다시 계산」처럼 요청이 하나만 있으면 되는 곳이 쓴다
     * (이미 대기 중인 요청은 커밋된 새 값으로 계산한다. 이미 실행 중인 요청은 옛 값으로 돌았을 수 있어 다음 정기 실행이 따라온다).
     */
    public Optional<Long> enqueueIfIdle(String jobKey, Long operatorId) {
        long id = ids.nextId();
        int inserted = jdbc.update("INSERT INTO batch_run_requests (id, job_key, kind, status, params, steps, source_run_id, operator_id, requested_at) "
                        + "VALUES (?, ?, ?, 'PENDING', NULL, NULL, NULL, ?, ?) ON CONFLICT DO NOTHING",
                id, jobKey, BatchRequestKind.NOW.name(), operatorId, ts(BatchJdbc.now()));
        return inserted > 0 ? Optional.of(id) : Optional.empty();
    }

    /** 그 배치에 끝나지 않은 요청이 있나 */
    public boolean hasOpen(String jobKey) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM batch_run_requests WHERE job_key = ? AND status IN ('PENDING', 'RUNNING'))",
                Boolean.class, jobKey));
    }

    /**
     * 가장 오래된 대기 요청 하나를 집어 {@code RUNNING} 으로 확정한다(따로 확정) — 다른 서버가 잠근 줄은 건너뛴다. {@code jobKeys} 는 이 서버가 돌릴 수 있는 배치
     * (업그레이드 중 옛 서버가 모르는 배치의 요청을 집지 않게). 없으면 빈 값.
     */
    public Optional<ClaimedRequest> claimNext(Collection<String> jobKeys) {
        if (jobKeys.isEmpty()) {
            return Optional.empty();
        }
        return requiresNew.execute(status -> {
            List<ClaimedRequest> found = jdbc.query(connection -> {
                PreparedStatement statement = connection.prepareStatement("SELECT id, job_key, kind, params, steps, source_run_id, operator_id FROM batch_run_requests "
                        + "WHERE status = 'PENDING' AND job_key = ANY (?) ORDER BY requested_at, id LIMIT 1 FOR UPDATE SKIP LOCKED");
                Array keys = connection.createArrayOf("varchar", jobKeys.toArray());
                statement.setArray(1, keys);
                return statement;
            }, (rs, row) -> new ClaimedRequest(rs.getLong("id"), rs.getString("job_key"), BatchRequestKind.valueOf(rs.getString("kind")),
                    BatchMessageCodec.decodeParams(rs.getString("params")), BatchMessageCodec.decodeSteps(rs.getString("steps")), nullableLong(rs, "source_run_id"),
                    nullableLong(rs, "operator_id")));
            if (found.isEmpty()) {
                return Optional.<ClaimedRequest>empty();
            }
            ClaimedRequest request = found.get(0);
            jdbc.update("UPDATE batch_run_requests SET status = 'RUNNING', started_at = ?, node_id = ? WHERE id = ?", ts(BatchJdbc.now()), nodeId, request.id());
            return Optional.of(request);
        });
    }

    /** 끝남 — 결과는 그 실행 로그. {@code runId} 가 null 이면 이미 이어 둔 값을 쓴다 */
    public void done(long requestId, Long runId) {
        requiresNew.executeWithoutResult(status -> jdbc.update(
                "UPDATE batch_run_requests SET status = 'DONE', run_id = coalesce(?, run_id), finished_at = ? WHERE id = ? AND status = 'RUNNING'",
                runId, ts(BatchJdbc.now()), requestId));
    }

    /** 거절 — 사유(문구 키 + 인자)를 남긴다 */
    public void reject(long requestId, MessageRef reason) {
        requiresNew.executeWithoutResult(status -> jdbc.update(
                "UPDATE batch_run_requests SET status = 'REJECTED', message_key = ?, message_args = ?, finished_at = ? WHERE id = ? AND status = 'RUNNING'",
                reason.key(), BatchMessageCodec.encodeArgs(reason), ts(BatchJdbc.now()), requestId));
    }

    /** 요청의 지금 상태(시험·점검용) */
    public Optional<BatchRequestStatus> statusOf(long requestId) {
        List<String> status = jdbc.queryForList("SELECT status FROM batch_run_requests WHERE id = ?", String.class, requestId);
        return status.isEmpty() ? Optional.empty() : Optional.of(BatchRequestStatus.valueOf(status.get(0)));
    }

    /**
     * 집은 지 {@code grace} 넘게 {@code RUNNING} 으로 남은 요청을 정리한다 — 서버가 뜰 때와 주기적으로(죽은 실행 닫기 {@link BatchRunner#sweepInterrupted} 다음에) 부른다.
     * <ul>
     *   <li>그 요청의 실행이 끝났으면 DONE(실행 번호를 잇는다).</li>
     *   <li>실행이 아직 실행 중이면 건드리지 않는다(죽은 실행이면 실행 닫기가 먼저 닫고 다음 번에 DONE 이 된다).</li>
     *   <li>실행이 없으면 그 배치의 잠금을 잡아 본다 — 잡히면(지금 아무도 그 배치를 돌리지 않는다) REJECTED({@value #INTERRUPTED_KEY}), 안 잡히면 다음 번에 다시 본다.</li>
     * </ul>
     *
     * @return 정리한 요청 수
     */
    public int sweepStale(Duration grace, BatchLockService locks) {
        Instant before = BatchJdbc.now().minus(grace);
        List<Object[]> rows = jdbc.query("SELECT q.id, q.job_key, coalesce(q.run_id, (SELECT r.id FROM batch_runs r WHERE r.request_id = q.id ORDER BY r.started_at DESC LIMIT 1)) "
                        + "AS run_id FROM batch_run_requests q WHERE q.status = 'RUNNING' AND q.started_at < ? ORDER BY q.started_at",
                (rs, row) -> new Object[] {rs.getLong("id"), rs.getString("job_key"), nullableLong(rs, "run_id")}, ts(before));
        int cleaned = 0;
        for (Object[] row : rows) {
            long requestId = (Long) row[0];
            String jobKey = (String) row[1];
            Long runId = (Long) row[2];
            if (runId != null) {
                String runStatus = jdbc.queryForList("SELECT status FROM batch_runs WHERE id = ?", String.class, runId).stream().findFirst().orElse(null);
                if (runStatus != null && !"RUNNING".equals(runStatus)) {
                    done(requestId, runId);
                    cleaned++;
                }
                continue;
            }
            Optional<BatchLock> lock = locks.tryAcquire(jobKey);
            if (lock.isEmpty()) {
                continue;
            }
            try (BatchLock held = lock.get()) {
                Integer changed = requiresNew.execute(status -> jdbc.update(
                        "UPDATE batch_run_requests SET status = 'REJECTED', message_key = ?, message_args = NULL, finished_at = ? "
                                + "WHERE id = ? AND status = 'RUNNING' AND run_id IS NULL AND NOT EXISTS (SELECT 1 FROM batch_runs r WHERE r.request_id = batch_run_requests.id)",
                        INTERRUPTED_KEY, ts(BatchJdbc.now()), requestId));
                if (changed != null && changed > 0) {
                    cleaned++;
                    log.warn("집은 서버가 끝맺지 못한 배치 요청을 거절로 닫았다 requestId={} jobKey={}", requestId, jobKey);
                }
            }
        }
        return cleaned;
    }
}
