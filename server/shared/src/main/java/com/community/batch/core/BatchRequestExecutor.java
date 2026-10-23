package com.community.batch.core;

import com.community.batch.core.BatchRequestQueue.ClaimedRequest;
import com.community.batch.core.BatchRunner.RunReport;
import com.community.batch.core.BatchRunner.RunRequest;
import com.community.batch.entity.BatchRequestKind;
import com.community.batch.entity.BatchRequestStatus;
import com.community.zconfiguration.message.MessageRef;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 집은 실행 요청을 실행한다 — Java 배치는 {@link BatchRunner}, DB 배치는 같은 저장 프로시저를 {@code CALL}({@link DbBatchCaller}). 끝나면 요청을 DONE(실행 번호)으로,
 * 돌 수 없었으면 REJECTED(사유)로 닫는다. 「언제 집을지」(주기·스레드)는 부르는 쪽(관리자 서버의 요청 집기 워커)이 정한다 — 그래서 배치를 별도 서버로 떼어도 이 클래스는 그대로다.
 *
 * <ul>
 *   <li>집을 때 같은 배치가 이미 돌고 있으면(잠금을 못 잡음) REJECTED({@value #RUNNING_KEY}).</li>
 *   <li>「실패 지점부터 다시」를 할 수 없거나(이미 이어 받음 등) 「실패 건만 다시」할 건이 없으면 REJECTED(그 사유).</li>
 *   <li>실행 자체의 실패(일이 예외)는 실행 로그가 FAIL 이고 요청은 DONE 이다. 실행을 시작하지도 못한 기반 오류(DB 끊김 등)는 REJECTED({@value #ERROR_KEY}).</li>
 * </ul>
 */
@Component
public class BatchRequestExecutor {

    private static final Logger log = LoggerFactory.getLogger(BatchRequestExecutor.class);

    public static final String RUNNING_KEY = "batch.request.rejected.running";
    public static final String ERROR_KEY = "batch.request.rejected.error";
    public static final String UNKNOWN_KEY = "batch.request.rejected.unknown";

    private final BatchRequestQueue queue;
    private final BatchRunner runner;
    private final BatchRegistry registry;
    private final DbBatchCaller database;
    private final BatchRunRecorder recorder;

    public BatchRequestExecutor(BatchRequestQueue queue, BatchRunner runner, BatchRegistry registry, DbBatchCaller database, BatchRunRecorder recorder) {
        this.queue = queue;
        this.runner = runner;
        this.registry = registry;
        this.database = database;
        this.recorder = recorder;
    }

    /** 이 서버가 집을 수 있는 배치 키 — 구현이 있는 Java 배치와 카탈로그의 DB 배치 */
    public List<String> claimableKeys() {
        List<String> keys = new ArrayList<>(registry.javaKeys());
        BatchCatalog.databaseJobs().forEach(definition -> keys.add(definition.key()));
        return keys;
    }

    /** 다음 대기 요청 하나를 집는다(없으면 빈 값) */
    public Optional<ClaimedRequest> claimNext() {
        return queue.claimNext(claimableKeys());
    }

    /** 집은 요청을 실행하고 닫는다. 돌려주는 값은 닫은 상태(DONE·REJECTED). 닫지도 못했으면 RUNNING — 남은 요청 정리가 나중에 닫는다 */
    public BatchRequestStatus execute(ClaimedRequest request) {
        try {
            BatchJob job = registry.jobOf(request.jobKey());
            if (job != null) {
                return executeJava(job, request);
            }
            BatchCatalog definition = BatchCatalog.ofKey(request.jobKey());
            if (definition == null || definition.isJava()) {
                return reject(request, UNKNOWN_KEY);
            }
            return executeDatabase(definition, request);
        } catch (RuntimeException e) {
            log.error("배치 요청을 실행하지 못했다 requestId={} jobKey={}", request.id(), request.jobKey(), e);
            try {
                return reject(request, ERROR_KEY);
            } catch (RuntimeException again) {
                log.error("배치 요청을 닫지 못했다 — 남은 요청 정리가 닫는다 requestId={}", request.id(), again);
                return BatchRequestStatus.RUNNING;
            }
        }
    }

    private BatchRequestStatus executeJava(BatchJob job, ClaimedRequest request) {
        BatchParameters parameters = BatchParameters.of(request.params());
        RunReport report = runner.runRequested(new RunRequest(job.definition(), request.kind(), request.operatorId(), parameters, request.steps(), request.sourceRunId(),
                request.id()));
        return switch (report.outcome()) {
            case FINISHED -> {
                queue.done(request.id(), report.runId());
                yield BatchRequestStatus.DONE;
            }
            case REJECTED -> reject(request, report.rejectKey());
            case ALREADY_RUNNING, NOT_DUE -> reject(request, RUNNING_KEY);
        };
    }

    private BatchRequestStatus executeDatabase(BatchCatalog definition, ClaimedRequest request) {
        if (request.kind() != BatchRequestKind.NOW) {
            return reject(request, request.kind() == BatchRequestKind.RESUME ? BatchRunner.NOT_RESUMABLE_KEY : BatchRunner.NO_FAILURES_KEY);
        }
        Long runId;
        try {
            runId = database.call(definition, request.operatorId());
        } catch (SQLException e) {
            // 프로시저 안에서 처리하지 못한 오류(로그 기록 자체의 실패 등) — 일의 실패는 프로시저가 FAIL 로그로 남기고 여기까지 오지 않는다
            throw new IllegalStateException("DB 배치를 실행하지 못했다: " + definition.key(), e);
        }
        if (runId == null) {
            return reject(request, RUNNING_KEY); // 같은 배치가 이미 돌고 있어 프로시저가 아무것도 하지 않았다
        }
        recorder.linkRequest(runId, request.id());
        queue.done(request.id(), runId);
        return BatchRequestStatus.DONE;
    }

    private BatchRequestStatus reject(ClaimedRequest request, String key) {
        queue.reject(request.id(), new MessageRef(key));
        log.info("배치 요청을 거절했다 requestId={} jobKey={} reason={}", request.id(), request.jobKey(), key);
        return BatchRequestStatus.REJECTED;
    }
}
