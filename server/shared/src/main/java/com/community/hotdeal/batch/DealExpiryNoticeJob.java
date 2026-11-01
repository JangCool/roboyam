package com.community.hotdeal.batch;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchStep;
import com.community.batch.core.BatchStepDefinition;
import com.community.hotdeal.repository.master.DealQueries;
import com.community.hotdeal.service.DealEndService;
import com.community.hotdeal.service.DealSettingsReader;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 마감 없는 딜의 기간 종료 알림 Java 배치 {@code hotdeal-expiry-notice}(서버 단계 고정 규칙 9 「자동 기간 종료 1회」) — 등록 뒤 {@code autoExpireDays}(0 = 끔)가 지난 마감 없는 딜의
 * 작성자에게 {@code DEAL_EXPIRED} 한 번({@code post_deals.expiry_notified_at}). 상태는 읽을 때 계산하므로 저장 상태는 바꾸지 않는다. 마감을 정한 딜은 작성자가 고른 마감이라 알리지 않는다.
 */
@Component
public class DealExpiryNoticeJob implements BatchJob {

    private final DealQueries queries;
    private final DealEndService ends;
    private final DealSettingsReader settings;

    public DealExpiryNoticeJob(DealQueries queries, DealEndService ends, DealSettingsReader settings) {
        this.queries = queries;
        this.ends = ends;
        this.settings = settings;
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.HOTDEAL_EXPIRY_NOTICE;
    }

    @Override
    public boolean resumable() {
        return true;
    }

    @Override
    public BatchResult run(BatchContext context) {
        BatchStep step = context.step(BatchStepDefinition.MAIN);
        int days = settings.current().autoExpireDays();
        if (days <= 0) {
            step.record(0, 0, 0);
            return BatchResult.of(new MessageRef("batch.result.hotdealExpiryNotice.off"));
        }
        Instant now = ends.now();
        Instant createdBefore = now.minus(Duration.ofDays(days));
        step.chunks(DealChunks.byPostId((after, size) -> queries.expiryCandidates(createdBefore, after, size)), items -> {
            long done = 0;
            for (Long postId : items) {
                done += ends.noticeExpired(postId, now, id -> queries.publicPost(id, now)) ? 1 : 0;
            }
            return done;
        });
        long noticed = step.processedCount();
        return BatchResult.of(noticed == 0 ? new MessageRef("batch.result.hotdealExpiryNotice.none")
                : new MessageRef("batch.result.hotdealExpiryNotice", String.valueOf(noticed)));
    }
}
