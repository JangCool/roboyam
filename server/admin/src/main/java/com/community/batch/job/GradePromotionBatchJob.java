package com.community.batch.job;

import com.community.batch.catalog.BatchCatalog;
import com.community.grade.service.GradePromotionService;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 등급 일수 승급 — 가입 후 일수가 지나 새로 조건을 채운 회원을 올린다({@link GradePromotionService#promoteByElapsedTime}를 부르기만 한다).
 * 처리 건수는 승급한 인원이다. 등급 설정을 저장할 때의 소급 승급은 일수 조건을 다음 배치로 미루므로(저장 전 일수와 새 일수 중 큰 값으로 판정) 기준을 낮춘 몫은 이 배치가 올린다.
 */
@Component
public class GradePromotionBatchJob implements BatchJob {

    private final GradePromotionService promotion;

    public GradePromotionBatchJob(GradePromotionService promotion) {
        this.promotion = promotion;
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.GRADE_PROMOTION;
    }

    @Override
    public BatchResult execute() {
        long promoted = promotion.promoteByElapsedTime(Instant.now());
        return BatchResult.of(promoted, promoted == 0 ? new MessageRef("batch.result.gradePromotion.none") : new MessageRef("batch.result.gradePromotion", String.valueOf(promoted)));
    }
}
