package com.community.batch.service;

import com.community.batch.catalog.BatchCatalog;
import com.community.batch.entity.BatchJobEntity;
import com.community.batch.repository.master.BatchJobRepository;
import com.community.permission.service.BootstrapLock;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서버가 뜰 때 배치 카탈로그(코드)를 {@code batch_jobs} 표와 맞춘다(권한 카탈로그와 같은 방식).
 *
 * <ul>
 *   <li>카탈로그에 있는데 표에 없는 배치는 기본값(사용 여부·cron)으로 넣는다.</li>
 *   <li>이미 있는 행은 <b>건드리지 않는다</b> — 운영자가 바꾼 사용 여부·cron 을 덮어쓰지 않는다. 그래서 새 릴리스가 카탈로그의 기본 cron 을 바꿔도 이미 설치된 곳에는 반영되지 않는다.</li>
 *   <li>카탈로그에서 사라진 배치의 행은 지운다(그 배치의 실행 로그는 보관 기간까지 남는다).</li>
 * </ul>
 * 서버 여러 대가 동시에 뜰 수 있어 기동 잠금({@link BootstrapLock})으로 한 번에 한 대씩 한다.
 */
@Component
public class BatchCatalogSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(BatchCatalogSynchronizer.class);

    private final BatchJobRepository jobs;
    private final EntityManager entityManager;

    public BatchCatalogSynchronizer(BatchJobRepository jobs, EntityManager entityManager) {
        this.jobs = jobs;
        this.entityManager = entityManager;
    }

    @Transactional
    public void sync() {
        BootstrapLock.acquire(entityManager);

        Instant now = Instant.now();
        Map<String, BatchJobEntity> existing = new HashMap<>();
        jobs.findAll().forEach(job -> existing.put(job.getJobKey(), job));

        int added = 0;
        for (BatchCatalog definition : BatchCatalog.values()) {
            if (existing.remove(definition.key()) == null) {
                jobs.persist(BatchJobEntity.create(definition.key(), definition.defaultEnabled(), definition.defaultCron(), now));
                added++;
            }
        }
        List<BatchJobEntity> stale = List.copyOf(existing.values()); // 카탈로그에 더는 없는 행
        jobs.deleteAll(stale);
        log.info("배치 카탈로그를 맞췄다: 새로 {}개, 지움 {}개 (카탈로그 {}개)", added, stale.size(), BatchCatalog.values().length);
    }
}
