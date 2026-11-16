package com.community.aimember.batch;

import com.community.aimember.service.AiNewsCollector;
import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchStep;
import com.community.batch.core.BatchStepDefinition;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.stereotype.Component;

/**
 * AI 기자 뉴스 소재 수집 Java 배치 {@code ai-news-collect}(ai-news-reporter-contract 「소재 수집」) — 켜진 뉴스 출처의 피드를 받아 새 항목을 넣고 7일 지난 항목을 지운다.
 * 일은 {@link AiNewsCollector}(관리자 「지금 수집」과 같은 서비스).
 *
 * <ul>
 *   <li>「지금 수집」이 돌고 있으면 건너뜀(문구 {@code batch.result.aiNewsCollect.busy}).</li>
 *   <li>출처 하나가 실패해도 나머지는 받는다 — 실패 수는 결과 문구에 싣고(출처 줄에 사유) 배치는 성공으로 끝낸다. 모든 출처가 실패하면 예외(FAIL — 배치 실패 알림).</li>
 * </ul>
 */
@Component
public class AiNewsCollectJob implements BatchJob {

    private final AiNewsCollector collector;

    public AiNewsCollectJob(AiNewsCollector collector) {
        this.collector = collector;
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.AI_NEWS_COLLECT;
    }

    @Override
    public BatchResult run(BatchContext context) {
        BatchStep step = context.step(BatchStepDefinition.MAIN);
        AiNewsCollector.Result r = collector.collectAll();
        if (r.busy()) {
            step.record(0, 0, 0);
            return BatchResult.of(new MessageRef("batch.result.aiNewsCollect.busy"));
        }
        step.record(r.sources(), r.added(), 0);
        if (r.sources() > 0 && r.failed() == r.sources()) {
            throw new IllegalStateException("뉴스 출처를 하나도 받지 못했다 — 출처 " + r.sources() + "개 모두 실패(사유는 AI 회원 설정 「뉴스 출처」)");
        }
        return BatchResult.of(new MessageRef("batch.result.aiNewsCollect", String.valueOf(r.sources()), String.valueOf(r.added()), String.valueOf(r.failed()),
                String.valueOf(r.deleted())));
    }
}
