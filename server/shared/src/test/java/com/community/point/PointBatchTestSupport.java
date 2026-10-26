package com.community.point;

import com.community.batch.core.BatchFailureRecorder;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchLockService;
import com.community.batch.core.BatchParameters;
import com.community.batch.core.BatchRegistry;
import com.community.batch.core.BatchRunRecorder;
import com.community.batch.core.BatchRunner;
import com.community.batch.core.BatchRunner.RunReport;
import com.community.batch.entity.BatchRequestKind;
import com.community.membernotification.repository.master.MemberNotificationWriter;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.point.repository.master.PointLotBatchQueries;
import com.community.zconfiguration.jpa.IsolatedSideWork;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * 포인트 배치 시험의 바탕 — {@link PointTestKit} 의 시험 DB·시계·서비스 위에서 배치 core({@link BatchRunner})로 실제 배치를 돌린다(스프링 문맥 없음).
 * 배치 키는 실제 카탈로그 키다(설정값 {@code batch_params} 도 그 키) — 시험이 바꾼 설정값은 스스로 지운다. 대상은 파라미터 {@code userId} 로 시험이 만든 회원에 묶는다(DB 를 다른 시험과 같이 쓴다).
 */
final class PointBatchTestSupport {

    /** 배치 실행 줄의 서버 번호(시험 전용) */
    private static final int NODE_ID = 902;

    final PointTestKit kit;
    final DataSourceTransactionManager transactions;
    final PointLotBatchQueries queries;
    final ObjectProvider<Clock> clocks;
    /** 회원 알림 메일 요청(커밋 뒤 발송 이벤트) — 시험은 모아 두기만 한다 */
    final List<Object> publishedEvents = new CopyOnWriteArrayList<>();
    final MemberNotificationPublisher publisher;

    PointBatchTestSupport(PointTestKit kit) {
        this.kit = kit;
        this.transactions = new DataSourceTransactionManager(kit.dataSource);
        this.queries = new PointLotBatchQueries(kit.dataSource);
        this.clocks = new StaticListableBeanFactory(Map.of("clock", kit.clock)).getBeanProvider(Clock.class);
        this.publisher = new MemberNotificationPublisher(new MemberNotificationWriter(kit.dataSource), new IsolatedSideWork(kit.dataSource, noFlush()), kit.ids,
                publishedEvents::add);
    }

    BatchRunner runner(BatchJob job) {
        return new BatchRunner(new BatchLockService(kit.dataSource), new BatchRunRecorder(kit.dataSource, transactions, kit.ids, NODE_ID),
                new BatchFailureRecorder(kit.dataSource, transactions, kit.ids), BatchRegistry.forJobs(List.of(job)), transactions);
    }

    /** 지금 실행 — 그 회원만 */
    RunReport runFor(BatchJob job, long userId) {
        return runner(job).runRequested(new BatchRunner.RunRequest(job.definition(), BatchRequestKind.NOW, null,
                BatchParameters.of(Map.of("userId", Long.toString(userId))), null, null, null));
    }

    /** 「실패 지점부터 다시」·「실패 건만 다시」 */
    RunReport rerun(BatchJob job, BatchRequestKind kind, long sourceRunId) {
        return runner(job).runRequested(new BatchRunner.RunRequest(job.definition(), kind, null, BatchParameters.empty(), null, sourceRunId, null));
    }

    /** 작업별 설정값(chunk_size·failure_limit) */
    void setting(String jobKey, String param, long value) {
        kit.jdbc.update("INSERT INTO batch_params (job_key, param_key, param_value) VALUES (?, ?, ?) ON CONFLICT (job_key, param_key) DO UPDATE SET param_value = EXCLUDED.param_value",
                jobKey, param, value);
    }

    void clearSetting(String jobKey, String param) {
        kit.jdbc.update("DELETE FROM batch_params WHERE job_key = ? AND param_key = ?", jobKey, param);
    }

    Map<String, Object> run(long runId) {
        return kit.jdbc.queryForMap("SELECT * FROM batch_runs WHERE id = ?", runId);
    }

    Map<String, Object> step(long runId) {
        return kit.jdbc.queryForMap("SELECT * FROM batch_run_steps WHERE run_id = ? AND step_name = 'main'", runId);
    }

    List<Map<String, Object>> failures(long runId) {
        return kit.jdbc.queryForList("SELECT * FROM batch_run_failures WHERE run_id = ? ORDER BY id", runId);
    }

    static long number(Object value) {
        return ((Number) value).longValue();
    }

    /** 영속성 컨텍스트가 없다 — {@code flush} 만 받는 대역(발행기의 세이브포인트가 부른다) */
    private static EntityManager noFlush() {
        return (EntityManager) Proxy.newProxyInstance(EntityManager.class.getClassLoader(), new Class<?>[] {EntityManager.class}, (proxy, method, args) -> switch (method.getName()) {
            case "flush" -> null;
            case "toString" -> "noFlushEntityManager";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }
}
