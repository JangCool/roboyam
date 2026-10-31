package com.community.bizapplication.batch;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchStep;
import com.community.batch.core.BatchStepDefinition;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.service.BizApplicationSchedules;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 결제 기한 만료 Java 배치 {@code biz-payment-expire}(설계 8절 · F2, 기본 10분마다) — 결제 기한이 지난 결제 대기 <b>전부</b>: 열린 결제를 먼저 닫고 → EXPIRED(자리 반납) +
 * 사업자 알림. 기한 전은 그대로. 건마다 {@link BizApplicationSchedules#expire}(멱등).
 */
@Component
public class BizPaymentExpireJob implements BatchJob {

    private final BizApplicationStore store;
    private final BizApplicationSchedules schedules;
    private final Clock clock;

    public BizPaymentExpireJob(BizApplicationStore store, BizApplicationSchedules schedules, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.schedules = schedules;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.BIZ_PAYMENT_EXPIRE;
    }

    @Override
    public boolean resumable() {
        return true;
    }

    @Override
    public BatchResult run(BatchContext context) {
        Instant now = clock.instant();
        BatchStep step = context.step(BatchStepDefinition.MAIN);
        step.chunks(BizChunks.byId((after, size) -> store.paymentOverdue(now, after, size),
                ids -> store.findAll(ids).stream().filter(a -> a.status() == BizApplicationStatus.AWAITING_PAYMENT).toList()), items -> {
            long done = 0;
            for (var app : items) {
                done += schedules.expire(app.id()) ? 1 : 0;
            }
            return done;
        });
        long done = step.processedCount();
        return BatchResult.of(done == 0 ? new MessageRef("batch.result.bizPaymentExpire.none") : new MessageRef("batch.result.bizPaymentExpire", String.valueOf(done)));
    }
}
