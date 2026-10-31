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
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 시작·종료 임박 알림 Java 배치 {@code biz-schedule-notice}(F9, 기본 1시간마다) — 단계 {@code start}: 시작이 24시간 안인 예약 · 단계 {@code end}: 끝이 24시간 안인 진행 중 예약.
 * 각각 아직 알리지 않았으면 사업자 알림 한 번(표시 칸 {@code start_notified_at}·{@code end_notified_at}).
 */
@Component
public class BizScheduleNoticeJob implements BatchJob {

    static final Duration WINDOW = Duration.ofHours(24);
    public static final String STEP_START = "start";
    public static final String STEP_END = "end";

    private final BizApplicationStore store;
    private final BizApplicationSchedules schedules;
    private final Clock clock;

    public BizScheduleNoticeJob(BizApplicationStore store, BizApplicationSchedules schedules, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.schedules = schedules;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.BIZ_SCHEDULE_NOTICE;
    }

    @Override
    public List<BatchStepDefinition> steps() {
        return List.of(new BatchStepDefinition(STEP_START, true), new BatchStepDefinition(STEP_END, true));
    }

    @Override
    public boolean resumable() {
        return true;
    }

    @Override
    public BatchResult run(BatchContext context) {
        Instant now = clock.instant();
        Instant until = now.plus(WINDOW);
        BatchStep start = context.step(STEP_START);
        start.chunks(BizChunks.byId((after, size) -> store.startingSoon(now, until, after, size),
                ids -> store.findAll(ids).stream().filter(a -> a.status() == BizApplicationStatus.SCHEDULED).toList()), items -> {
            long done = 0;
            for (var app : items) {
                done += schedules.noticeStart(app) ? 1 : 0;
            }
            return done;
        });
        BatchStep end = context.step(STEP_END);
        end.chunks(BizChunks.byId((after, size) -> store.endingSoon(now, until, after, size),
                ids -> store.findAll(ids).stream().filter(a -> a.status() == BizApplicationStatus.SCHEDULED).toList()), items -> {
            long done = 0;
            for (var app : items) {
                done += schedules.noticeEnd(app) ? 1 : 0;
            }
            return done;
        });
        long started = start.processedCount();
        long ending = end.processedCount();
        return BatchResult.of(started + ending == 0 ? new MessageRef("batch.result.bizScheduleNotice.none")
                : new MessageRef("batch.result.bizScheduleNotice", String.valueOf(started), String.valueOf(ending)));
    }
}
