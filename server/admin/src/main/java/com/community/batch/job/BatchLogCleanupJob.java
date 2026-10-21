package com.community.batch.job;

import com.community.batch.catalog.BatchCatalog;
import com.community.batch.config.BatchProperties;
import com.community.batch.repository.master.BatchRunRepositoryQDSL;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 보관 기간({@code roboyam.batch.log-retention-days}, 기본 90일)이 지난 배치 실행 로그를 지운다. 실행 중인 로그는 지우지 않는다. 처리 건수는 지운 줄 수 */
@Component
public class BatchLogCleanupJob implements BatchJob {

    private final BatchRunRepositoryQDSL runQueries;
    private final BatchProperties properties;

    public BatchLogCleanupJob(BatchRunRepositoryQDSL runQueries, BatchProperties properties) {
        this.runQueries = runQueries;
        this.properties = properties;
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.BATCH_LOG_CLEANUP;
    }

    @Override
    @Transactional
    public BatchResult execute() {
        long deleted = runQueries.deleteFinishedBefore(Instant.now().minus(Duration.ofDays(properties.logRetentionDays())));
        String days = String.valueOf(properties.logRetentionDays());
        return BatchResult.of(deleted, deleted == 0 ? new MessageRef("batch.result.logCleanup.none", days) : new MessageRef("batch.result.logCleanup", String.valueOf(deleted), days));
    }
}
