package com.community.batch.service;

import com.community.batch.catalog.BatchCatalog;
import com.community.batch.job.BatchJob;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 카탈로그의 배치와 구현({@link BatchJob} 빈)을 짝짓는다. 카탈로그 한 줄에 구현이 없거나 둘 이상이면 서버가 뜨지 않는다 — 정의만 있고 도는 일이 없는 배치(또는 그 반대)가
 * 생기지 않게 한다.
 */
@Component
public class BatchRegistry {

    private final Map<BatchCatalog, BatchJob> jobs = new EnumMap<>(BatchCatalog.class);

    public BatchRegistry(List<BatchJob> beans) {
        for (BatchJob job : beans) {
            BatchJob previous = jobs.put(job.definition(), job);
            if (previous != null) {
                throw new IllegalStateException("배치 구현이 둘 이상이다: " + job.definition());
            }
        }
        for (BatchCatalog definition : BatchCatalog.values()) {
            if (!jobs.containsKey(definition)) {
                throw new IllegalStateException("카탈로그에 있는 배치의 구현(BatchJob 빈)이 없다: " + definition);
            }
        }
    }

    public BatchJob jobOf(BatchCatalog definition) {
        return jobs.get(definition);
    }
}
