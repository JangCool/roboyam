package com.community.bizapplication.batch;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchStep;
import com.community.batch.core.BatchStepDefinition;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizDealPostStore;
import com.community.bizapplication.service.BizHotdealPublisher;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 사업자 핫딜 글 게시 Java 배치 {@code biz-hotdeal-publish}(biz-hotdeal-contract 규칙 1~3, 기본 1분마다) — 시작이 지난 예약 HOTDEAL_POST 건 중 게시 결과가 없는 것 전부를
 * 건마다 {@link BizHotdealPublisher#publishDue}(새 트랜잭션 · 행 잠금 · 재판정 — 멱등). 결과 줄이 생기면 대상에서 빠지므로 처음부터 다시 돌려도 안전하다.
 */
@Component
public class BizHotdealPublishJob implements BatchJob {

    private final BizDealPostStore results;
    private final BizApplicationStore store;
    private final BizHotdealPublisher publisher;
    private final Clock clock;

    public BizHotdealPublishJob(BizDealPostStore results, BizApplicationStore store, BizHotdealPublisher publisher, ObjectProvider<Clock> clocks) {
        this.results = results;
        this.store = store;
        this.publisher = publisher;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.BIZ_HOTDEAL_PUBLISH;
    }

    @Override
    public boolean resumable() {
        return true;
    }

    @Override
    public BatchResult run(BatchContext context) {
        Instant now = clock.instant();
        AtomicLong posted = new AtomicLong();
        AtomicLong failed = new AtomicLong();
        BatchStep step = context.step(BatchStepDefinition.MAIN);
        step.chunks(BizChunks.byId((after, size) -> store.findAll(results.due(now, after, size)).stream()
                .sorted(java.util.Comparator.comparingLong(BizApplicationStore.AppRow::id)).toList(), store::findAll), items -> {
            long done = 0;
            for (var app : items) {
                switch (publisher.publishDue(app.id())) {
                    case POSTED -> {
                        posted.incrementAndGet();
                        done++;
                    }
                    case FAILED -> {
                        failed.incrementAndGet();
                        done++;
                    }
                    case SKIPPED -> {
                    }
                }
            }
            return done;
        });
        if (posted.get() + failed.get() == 0) {
            return BatchResult.of(new MessageRef("batch.result.bizHotdealPublish.none"));
        }
        return BatchResult.of(new MessageRef("batch.result.bizHotdealPublish", String.valueOf(posted.get()), String.valueOf(failed.get())));
    }
}
