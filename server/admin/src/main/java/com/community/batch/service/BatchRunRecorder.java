package com.community.batch.service;

import com.community.batch.dto.BatchLastRun;
import com.community.batch.entity.BatchRunEntity;
import com.community.batch.entity.BatchRunStatus;
import com.community.batch.entity.BatchTriggerType;
import com.community.batch.repository.master.BatchJobRepositoryQDSL;
import com.community.batch.repository.master.BatchRunRepository;
import com.community.batch.repository.master.BatchRunRepositoryQDSL;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배치 실행 로그와 목록의 「마지막 실행 요약」을 쓴다. 배치 일 자체와는 <b>별도 트랜잭션</b>이다 — 실행기가 트랜잭션 밖에서 부르므로 시작 줄은 배치가 도는 동안에도 다른 곳에서 보이고
 * (실행 중 표시), 배치가 실패해 롤백돼도 「실패했다」는 남는다.
 */
@Service
public class BatchRunRecorder {

    /** 시작한 실행 — 끝낼 때 이 값으로 줄을 찾는다 */
    public record Started(long runId, Instant startedAt) {
    }

    /** 서버가 죽어 끝맺지 못한 실행을 닫을 때 쓰는 요약 문구 키 */
    public static final String INTERRUPTED_KEY = "batch.error.interrupted";

    private final BatchRunRepository runs;
    private final BatchRunRepositoryQDSL runQueries;
    private final BatchJobRepositoryQDSL jobQueries;
    private final SnowflakeIdGenerator ids;
    private final int nodeId;

    public BatchRunRecorder(BatchRunRepository runs, BatchRunRepositoryQDSL runQueries, BatchJobRepositoryQDSL jobQueries, SnowflakeIdGenerator ids,
                            @Value("${roboyam.node-id}") int nodeId) {
        this.runs = runs;
        this.runQueries = runQueries;
        this.jobQueries = jobQueries;
        this.ids = ids;
        this.nodeId = nodeId;
    }

    /**
     * 실행 중 줄을 넣는다. 같은 배치가 이미 실행 중이거나(유일 색인) 같은 예약 시각의 줄이 이미 있으면 {@code DataIntegrityViolationException} 이 난다 —
     * 부르는 쪽이 「건너뜀」으로 다룬다.
     */
    @Transactional
    public Started start(String jobKey, BatchTriggerType trigger, Instant scheduledAt, Long operatorId) {
        Instant startedAt = Instant.now().truncatedTo(ChronoUnit.MICROS); // DB 시각 정밀도에 맞춘다
        long runId = ids.nextId();
        runs.persistAndFlush(BatchRunEntity.start(runId, jobKey, trigger, scheduledAt, startedAt, operatorId, nodeId));
        jobQueries.updateLastRun(jobKey, new BatchLastRun(startedAt, BatchRunStatus.RUNNING.name(), null, null, null, null));
        return new Started(runId, startedAt);
    }

    /** 실행을 끝낸다 — {@code status} 는 OK · SKIP · FAIL. 실패는 {@code error}(이미 가린 한 줄)를, 그 밖은 {@code message} 요약을 싣는다 */
    @Transactional
    public void finish(Started started, String jobKey, BatchRunStatus status, Long affected, MessageRef message, String error) {
        Instant finishedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String messageKey = message == null ? null : message.key();
        String messageArgs = message == null ? null : BatchMessageCodec.encodeArgs(message);
        BatchRunEntity run = runs.findById(started.runId()).orElseThrow(() -> new IllegalStateException("실행 로그 줄이 없다: " + started.runId()));
        run.finish(status, finishedAt, affected, messageKey, messageArgs, error);
        jobQueries.updateLastRun(jobKey, new BatchLastRun(started.startedAt(), status.name(), affected, messageKey, messageArgs, error));
    }

    /**
     * 그 배치의 「실행 중」으로 남은 줄을 실패로 닫는다(서버가 죽어 끝맺지 못한 실행). <b>그 배치의 잠금을 쥔 쪽만</b> 부른다.
     *
     * @return 닫은 줄 수
     */
    @Transactional
    public long failInterrupted(String jobKey) {
        long closed = runQueries.failRunning(jobKey, Instant.now().truncatedTo(ChronoUnit.MICROS), INTERRUPTED_KEY);
        if (closed > 0) {
            jobQueries.failRunningSummary(jobKey, INTERRUPTED_KEY);
        }
        return closed;
    }
}
