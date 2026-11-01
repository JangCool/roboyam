package com.community.hotdeal.batch;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchStep;
import com.community.batch.core.BatchStepDefinition;
import com.community.hotdeal.service.DealCleanupService;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 핫딜 정리 Java 배치 {@code hotdeal-cleanup} — 단계 {@code og}: OG 사본(D1 — 딜 종료·글 삭제 90일 뒤, 그 사본을 쓰는 다른 딜이 없을 때만, 글에 붙지 않은 사본은 7일 뒤, 파일은 확정 뒤 삭제) ·
 * 단계 {@code revisions}: 딜 칸 수정 이력 6개월(D3). 정리는 {@link DealCleanupService}. 저장소 삭제는 되돌릴 수 없어 덩어리 처리 도우미를 쓰지 않는다(파일 정리 배치와 같다) —
 * 건별 실패·「실패 지점부터 다시」는 없고 다음 실행이 남은 것을 다시 집는다.
 */
@Component
public class DealCleanupJob implements BatchJob {

    public static final String STEP_OG = "og";
    public static final String STEP_REVISIONS = "revisions";

    private final DealCleanupService cleanup;
    private final Clock clock;

    public DealCleanupJob(DealCleanupService cleanup, ObjectProvider<Clock> clocks) {
        this.cleanup = cleanup;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.HOTDEAL_CLEANUP;
    }

    @Override
    public List<BatchStepDefinition> steps() {
        return List.of(new BatchStepDefinition(STEP_OG, true), new BatchStepDefinition(STEP_REVISIONS, true));
    }

    @Override
    public BatchResult run(BatchContext context) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        BatchStep og = context.step(STEP_OG);
        long images = 0;
        if (og.active()) {
            DealCleanupService.OgTally tally = cleanup.cleanOgImages(now);
            og.record(tally.examined(), tally.removed(), tally.examined() - tally.removed());
            images = tally.removed();
        }
        BatchStep revisions = context.step(STEP_REVISIONS);
        long rows = 0;
        if (revisions.active()) {
            rows = cleanup.cleanRevisions(now);
            revisions.record(rows, rows, 0);
        }
        return BatchResult.of(images + rows == 0 ? new MessageRef("batch.result.hotdealCleanup.none")
                : new MessageRef("batch.result.hotdealCleanup", String.valueOf(images), String.valueOf(rows)));
    }
}
