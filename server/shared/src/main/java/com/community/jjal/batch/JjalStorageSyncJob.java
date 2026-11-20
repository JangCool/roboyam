package com.community.jjal.batch;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchStep;
import com.community.batch.core.BatchStepDefinition;
import com.community.jjal.repository.master.JjalRepositoryQDSL;
import com.community.jjal.service.JjalStorageSync;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 짤 저장 위치 맞추기(Java 배치 {@code jjal-storage-sync} — 한 단계 {@code main}, 10분마다). 업무 트랜잭션의 커밋 뒤 옮기기({@link JjalStorageSync#afterCommit})가 놓친 것
 * (서버 멈춤·저장소 오류)과 시각이 지나 바뀌는 것(권리침해 임시조치가 끝나 다시 보일 짤 — 새 무작위 공개 키로)을 맞춘다. 파일마다 {@link JjalStorageSync#sync}(행을 다시 읽어 판정)를 부른다.
 * 덩어리 처리 도우미를 쓰지 않는다(저장소 작업은 되돌릴 수 없다). 단계 건수: 읽음 = 후보 수, 처리 = 옮기거나 지우거나 상태를 정리한 수, 건너뜀 = 나머지(이미 맞음·실패 — 실패는 다음 실행이 다시).
 */
@Component
public class JjalStorageSyncJob implements BatchJob {

    private static final int CHUNK = 100;

    private final JjalRepositoryQDSL queries;
    private final JjalStorageSync sync;
    private final TransactionTemplate tx;

    public JjalStorageSyncJob(JjalRepositoryQDSL queries, JjalStorageSync sync, PlatformTransactionManager txManager) {
        this.queries = queries;
        this.sync = sync;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setReadOnly(true);
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.JJAL_STORAGE_SYNC;
    }

    @Override
    public BatchResult run(BatchContext context) {
        BatchStep step = context.step(BatchStepDefinition.MAIN);
        Instant now = Instant.now();
        long examined = 0;
        long changed = 0;
        long afterId = 0;
        while (true) {
            long from = afterId;
            List<Long> candidates = tx.execute(status -> queries.storageOutOfSync(now, from, CHUNK));
            if (candidates == null || candidates.isEmpty()) {
                break;
            }
            for (Long id : candidates) {
                examined++;
                if (sync.syncQuietly(id)) {
                    changed++;
                }
            }
            afterId = candidates.get(candidates.size() - 1);
            if (candidates.size() < CHUNK) {
                break;
            }
        }
        step.record(examined, changed, examined - changed);
        return BatchResult.of(changed == 0 ? new MessageRef("batch.result.jjalStorageSync.none") : new MessageRef("batch.result.jjalStorageSync", String.valueOf(changed)));
    }
}
