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
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 결제 기한 임박 알림 Java 배치 {@code biz-payment-due-notice}(F9, 기본 1시간마다) — 결제 기한이 24시간 안이고 아직 알리지 않은 결제 대기마다 사업자 알림 한 번
 * (표시 칸 {@code payment_due_notified_at} — 조건부로 적어 겹쳐 돌아도 한 번).
 */
@Component
public class BizPaymentDueNoticeJob implements BatchJob {

    static final Duration WINDOW = Duration.ofHours(24);

    private final BizApplicationStore store;
    private final BizApplicationSchedules schedules;
    private final Clock clock;

    public BizPaymentDueNoticeJob(BizApplicationStore store, BizApplicationSchedules schedules, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.schedules = schedules;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.BIZ_PAYMENT_DUE_NOTICE;
    }

    @Override
    public boolean resumable() {
        return true;
    }

    @Override
    public BatchResult run(BatchContext context) {
        Instant now = clock.instant();
        BatchStep step = context.step(BatchStepDefinition.MAIN);
        step.chunks(BizChunks.byId((after, size) -> store.paymentDueSoon(now, now.plus(WINDOW), after, size),
                ids -> store.findAll(ids).stream().filter(a -> a.status() == BizApplicationStatus.AWAITING_PAYMENT).toList()), items -> {
            long done = 0;
            for (var app : items) {
                done += schedules.noticePaymentDue(app) ? 1 : 0;
            }
            return done;
        });
        long done = step.processedCount();
        return BatchResult.of(done == 0 ? new MessageRef("batch.result.bizPaymentDueNotice.none") : new MessageRef("batch.result.bizPaymentDueNotice", String.valueOf(done)));
    }
}
