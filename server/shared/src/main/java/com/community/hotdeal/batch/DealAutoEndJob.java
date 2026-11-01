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
 * 의심 딜 자동 종료 Java 배치 {@code hotdeal-auto-end}(hotdeal-contract 「배치」, 확정 가정 — 유예 {@code endGraceHours}, 0 = 자동 종료 안 함) — 의심이 된 지 유예가 지났고 그새
 * 「아직 진행 중」·종료가 없는 딜을 자동 종료(AUTO) + 의심 구간 요청자 보상 + 작성자 알림 {@code DEAL_AUTO_ENDED}. 건마다 딜 행을 잠가 다시 본다({@link DealEndService#autoEnd}).
 * 「그날 몫」이 아니라 지난 것 전부를 고르므로 놓친 실행을 다음 실행이 메꾼다.
 */
@Component
public class DealAutoEndJob implements BatchJob {

    private final DealQueries queries;
    private final DealEndService ends;
    private final DealSettingsReader settings;

    public DealAutoEndJob(DealQueries queries, DealEndService ends, DealSettingsReader settings) {
        this.queries = queries;
        this.ends = ends;
        this.settings = settings;
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.HOTDEAL_AUTO_END;
    }

    @Override
    public boolean resumable() {
        return true;
    }

    @Override
    public BatchResult run(BatchContext context) {
        BatchStep step = context.step(BatchStepDefinition.MAIN);
        int grace = settings.current().endGraceHours();
        if (grace <= 0) {
            step.record(0, 0, 0);
            return BatchResult.of(new MessageRef("batch.result.hotdealAutoEnd.off"));
        }
        Instant now = ends.now();
        Instant flaggedBefore = now.minus(Duration.ofHours(grace));
        step.chunks(DealChunks.byPostId((after, size) -> queries.autoEndCandidates(flaggedBefore, after, size)), items -> {
            long done = 0;
            for (Long postId : items) {
                done += ends.autoEnd(postId, now) ? 1 : 0;
            }
            return done;
        });
        long ended = step.processedCount();
        return BatchResult.of(ended == 0 ? new MessageRef("batch.result.hotdealAutoEnd.none") : new MessageRef("batch.result.hotdealAutoEnd", String.valueOf(ended)));
    }
}
