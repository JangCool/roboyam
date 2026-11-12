package com.community.batch.core;

import com.community.batch.core.BatchLockService.BatchLock;
import com.community.batch.entity.BatchRequestKind;
import com.community.batch.entity.BatchRequestStatus;
import com.community.batch.entity.BatchRunRequestEntity;
import com.community.batch.entity.BatchRunStatus;
import com.community.batch.entity.QBatchRunEntity;
import com.community.batch.entity.QBatchRunRequestEntity;
import com.community.batch.repository.master.BatchRunRequestRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.JpaMasterConfig;
import com.community.zconfiguration.message.MessageRef;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실행 요청 표({@code batch_run_requests}) — 화면의 「지금 실행」·「실패 지점부터 다시」·「실패 건만 다시」가 줄을 넣고({@link #enqueue}), 배치를 켠 서버가 집는다({@link #claimNext}).
 * 같은 서버든 다른 서버든 같은 방식이라 배치를 별도 서버로 떼어도 새 장치가 필요 없다(docs/domain/commerce.md 15-2, 결정 6·7).
 *
 * <ul>
 *   <li>같은 배치에 끝나지 않은(PENDING·RUNNING) 요청은 하나만 — 부분 유일 색인({@code ux_batch_run_requests_open})이 막고, 두 번째는 {@link #enqueue} 가 빈 값을 돌려준다.</li>
 *   <li>집기는 엔티티 잠금 + {@code SKIP LOCKED}({@code FOR NO KEY UPDATE SKIP LOCKED}) — 서버 여러 대가 동시에 집어도 한 요청은 한 서버만 집는다. 집은 요청은 곧바로 {@code RUNNING}
 *       (집은 서버의 node_id)으로 확정한다.</li>
 *   <li>집은 서버가 죽어 {@code RUNNING} 으로 남은 요청은 {@link #sweepStale} 이 정리한다 — 실행이 이미 끝났으면 DONE, 실행이 없고 그 배치의 잠금이 비어 있으면 REJECTED.</li>
 * </ul>
 * JPA·QueryDSL 로 쓴다(트랜잭션 틀은 {@link BatchTransactions}). Native SQL 로 남긴 것: 「끝나지 않은 요청이 없을 때만 넣기」({@code ON CONFLICT DO NOTHING} — 트랜잭션을 망가뜨리지 않는다).
 */
@Component
public class BatchRequestQueue {

    private static final Logger log = LoggerFactory.getLogger(BatchRequestQueue.class);

    /** 집은 서버가 사라져 끝맺지 못한 요청의 거절 사유 */
    public static final String INTERRUPTED_KEY = "batch.request.rejected.interrupted";

    /** 집은 요청 하나 */
    public record ClaimedRequest(long id, String jobKey, BatchRequestKind kind, Map<String, String> params, List<String> steps, Long sourceRunId, Long operatorId) {
    }

    private static final QBatchRunRequestEntity Q = QBatchRunRequestEntity.batchRunRequestEntity;
    private static final QBatchRunEntity RUN = QBatchRunEntity.batchRunEntity;

    private final JPAQueryFactory query;
    private final BatchRunRequestRepository requests;
    private final EntityManager entityManager;
    private final TransactionTemplate requiresNew;
    private final TransactionTemplate required;
    private final SnowflakeIdGenerator ids;
    private final int nodeId;

    @Autowired
    public BatchRequestQueue(@Qualifier(JpaMasterConfig.QUERY_FACTORY) JPAQueryFactory query, BatchRunRequestRepository requests, EntityManager entityManager,
                             PlatformTransactionManager transactions, SnowflakeIdGenerator ids, @Value("${irobit.node-id}") int nodeId) {
        this.query = query;
        this.requests = requests;
        this.entityManager = entityManager;
        this.requiresNew = BatchTransactions.requiresNew(transactions);
        this.required = BatchTransactions.required(transactions);
        this.ids = ids;
        this.nodeId = nodeId;
    }

    /**
     * 요청 한 줄을 넣는다 — <b>부르는 쪽 트랜잭션에 참여한다</b>(감사 기록과 함께 확정). 같은 배치에 끝나지 않은 요청이 이미 있으면 빈 값(부르는 쪽이 409 로 답한다 —
     * 그 트랜잭션은 롤백 전용이 되므로 부르는 쪽은 곧바로 예외를 던져 롤백해야 한다). 넣고 바로 내보내 유일 색인 위반을 그 자리에서 본다.
     */
    public Optional<Long> enqueue(String jobKey, BatchRequestKind kind, Map<String, String> params, List<String> steps, Long sourceRunId, Long operatorId) {
        long id = ids.nextId();
        try {
            required.executeWithoutResult(status -> {
                requests.persist(BatchRunRequestEntity.pending(id, jobKey, kind, BatchMessageCodec.encodeParams(params), BatchMessageCodec.encodeSteps(steps), sourceRunId,
                        operatorId, BatchTransactions.now()));
                requests.flush();
            });
            return Optional.of(id);
        } catch (DataIntegrityViolationException e) {
            if (BatchTransactions.uniqueViolation(e)) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * 끝나지 않은 요청이 없을 때만 「지금 실행」 요청 한 줄을 넣는다(파라미터·단계 없음) — <b>부르는 쪽 트랜잭션에 참여하고, 이미 있으면 실패 없이 빈 값</b>
     * ({@code ON CONFLICT DO NOTHING} 이라 트랜잭션을 망가뜨리지 않는다). 업무 저장 뒤 「바로 다시 계산」처럼 요청이 하나만 있으면 되는 곳이 쓴다
     * (이미 대기 중인 요청은 커밋된 새 값으로 계산한다. 이미 실행 중인 요청은 옛 값으로 돌았을 수 있어 다음 정기 실행이 따라온다).
     *
     * <p>Native SQL 인 이유: {@code INSERT … ON CONFLICT DO NOTHING} — 부분 유일 색인 충돌을 실패 없이 넘기는 넣기는 JPQL·QueryDSL 에 없다(넣어 보고 실패를 잡으면 트랜잭션이 깨진다).
     */
    public Optional<Long> enqueueIfIdle(String jobKey, Long operatorId) {
        long id = ids.nextId();
        Integer inserted = required.execute(status -> entityManager.createNativeQuery(
                        "INSERT INTO batch_run_requests (id, job_key, kind, status, params, steps, source_run_id, operator_id, requested_at) "
                                + "VALUES (?1, ?2, ?3, 'PENDING', NULL, NULL, NULL, ?4, ?5) ON CONFLICT DO NOTHING")
                .setParameter(1, id).setParameter(2, jobKey).setParameter(3, BatchRequestKind.NOW.name()).setParameter(4, operatorId)
                .setParameter(5, BatchTransactions.ts(BatchTransactions.now())).executeUpdate());
        return inserted != null && inserted > 0 ? Optional.of(id) : Optional.empty();
    }

    /** 그 배치에 끝나지 않은 요청이 있나 */
    public boolean hasOpen(String jobKey) {
        return query.selectOne().from(Q).where(Q.jobKey.eq(jobKey), Q.status.in(BatchRequestStatus.PENDING, BatchRequestStatus.RUNNING)).fetchFirst() != null;
    }

    /**
     * 가장 오래된 대기 요청 하나를 집어 {@code RUNNING} 으로 확정한다(따로 확정) — 다른 서버가 잠근 줄은 건너뛴다. {@code jobKeys} 는 이 서버가 돌릴 수 있는 배치
     * (업그레이드 중 옛 서버가 모르는 배치의 요청을 집지 않게). 없으면 빈 값. 잠금은 요청 표만 고르는 한 문장(id), 칸은 그 뒤에 읽는다.
     */
    public Optional<ClaimedRequest> claimNext(Collection<String> jobKeys) {
        if (jobKeys.isEmpty()) {
            return Optional.empty();
        }
        return requiresNew.execute(status -> {
            // 잠금 세기: PESSIMISTIC_WRITE = FOR NO KEY UPDATE SKIP LOCKED(전환 전 FOR UPDATE SKIP LOCKED). batch_requests 를 FK 로 가리키는 표가 없고(KEY SHARE 를 잡는
            // 쪽이 없다) 요청 줄을 지우거나 키를 바꾸는 쪽도 FOR UPDATE 와 똑같이 막히므로 건너뛰는 줄·막히는 쪽이 전환 전과 같다 — 그대로 둔다
            Long claimed = query.select(Q.id).from(Q).where(Q.status.eq(BatchRequestStatus.PENDING), Q.jobKey.in(jobKeys)).orderBy(Q.requestedAt.asc(), Q.id.asc())
                    .limit(1).setLockMode(LockModeType.PESSIMISTIC_WRITE).setHint(BatchTransactions.LOCK_TIMEOUT, BatchTransactions.SKIP_LOCKED).fetchFirst();
            if (claimed == null) {
                return Optional.<ClaimedRequest>empty();
            }
            Tuple t = query.select(Q.id, Q.jobKey, Q.kind, Q.params, Q.steps, Q.sourceRunId, Q.operatorId).from(Q).where(Q.id.eq(claimed)).fetchOne();
            ClaimedRequest request = new ClaimedRequest(t.get(Q.id), t.get(Q.jobKey), t.get(Q.kind), BatchMessageCodec.decodeParams(t.get(Q.params)),
                    BatchMessageCodec.decodeSteps(t.get(Q.steps)), t.get(Q.sourceRunId), t.get(Q.operatorId));
            query.update(Q).set(Q.status, BatchRequestStatus.RUNNING).set(Q.startedAt, BatchTransactions.now()).set(Q.nodeId, nodeId).where(Q.id.eq(claimed)).execute();
            return Optional.of(request);
        });
    }

    /** 끝남 — 결과는 그 실행 로그. {@code runId} 가 null 이면 이미 이어 둔 값을 쓴다 */
    public void done(long requestId, Long runId) {
        requiresNew.executeWithoutResult(status -> {
            JPAUpdateClause update = query.update(Q).set(Q.status, BatchRequestStatus.DONE).set(Q.finishedAt, BatchTransactions.now());
            if (runId != null) {
                update.set(Q.runId, runId);
            }
            update.where(Q.id.eq(requestId), Q.status.eq(BatchRequestStatus.RUNNING)).execute();
        });
    }

    /** 거절 — 사유(문구 키 + 인자)를 남긴다 */
    public void reject(long requestId, MessageRef reason) {
        requiresNew.executeWithoutResult(status -> query.update(Q).set(Q.status, BatchRequestStatus.REJECTED).set(Q.messageKey, reason.key())
                .set(Q.messageArgs, BatchMessageCodec.encodeArgs(reason)).set(Q.finishedAt, BatchTransactions.now())
                .where(Q.id.eq(requestId), Q.status.eq(BatchRequestStatus.RUNNING)).execute());
    }

    /** 요청의 지금 상태(시험·점검용) */
    public Optional<BatchRequestStatus> statusOf(long requestId) {
        return Optional.ofNullable(query.select(Q.status).from(Q).where(Q.id.eq(requestId)).fetchOne());
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
        Instant before = BatchTransactions.now().minus(grace);
        List<Tuple> rows = query.select(Q.id, Q.jobKey, Q.runId).from(Q).where(Q.status.eq(BatchRequestStatus.RUNNING), Q.startedAt.lt(before))
                .orderBy(Q.startedAt.asc()).fetch();
        int cleaned = 0;
        for (Tuple row : rows) {
            long requestId = row.get(Q.id);
            String jobKey = row.get(Q.jobKey);
            // 요청에 이어 둔 실행 — 없으면 그 요청이 만든 가장 최근 실행
            Long runId = row.get(Q.runId) != null ? row.get(Q.runId)
                    : query.select(RUN.id).from(RUN).where(RUN.requestId.eq(requestId)).orderBy(RUN.startedAt.desc()).limit(1).fetchFirst();
            if (runId != null) {
                BatchRunStatus runStatus = query.select(RUN.status).from(RUN).where(RUN.id.eq(runId)).fetchFirst();
                if (runStatus != null && runStatus != BatchRunStatus.RUNNING) {
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
                Long changed = requiresNew.execute(status -> query.update(Q).set(Q.status, BatchRequestStatus.REJECTED).set(Q.messageKey, INTERRUPTED_KEY)
                        .setNull(Q.messageArgs).set(Q.finishedAt, BatchTransactions.now())
                        .where(Q.id.eq(requestId), Q.status.eq(BatchRequestStatus.RUNNING), Q.runId.isNull(),
                                JPAExpressions.selectOne().from(RUN).where(RUN.requestId.eq(Q.id)).notExists())
                        .execute());
                if (changed != null && changed > 0) {
                    cleaned++;
                    log.warn("집은 서버가 끝맺지 못한 배치 요청을 거절로 닫았다 requestId={} jobKey={}", requestId, jobKey);
                }
            }
        }
        return cleaned;
    }
}
