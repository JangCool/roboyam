package com.community.hotdeal.batch;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchStep;
import com.community.batch.core.BatchStepDefinition;
import com.community.hotdeal.service.DealSuspicionService;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 위장 홍보 의심 후보 계산 Java 배치 {@code hotdeal-suspicion}(확정 가정 「위장 홍보 후보」) — 단계 {@code member}: 회원 후보 · 단계 {@code store}: 판매처 후보. 계산·맞추기·관리자 알림은
 * {@link DealSuspicionService}(단계마다 한 트랜잭션 — 후보 수가 적은 집계라 덩어리로 나누지 않는다). 자동 제재 없음.
 * 단계 건수: 읽음 = 조건에 맞은 대상 수, 처리 = 새로 연 후보 수, 건너뜀 = 이미 열려 있거나 무시한 뒤 새 글이 없는 대상.
 */
@Component
public class DealSuspicionJob implements BatchJob {

    public static final String STEP_MEMBER = "member";
    public static final String STEP_STORE = "store";

    private final DealSuspicionService suspicions;
    private final Clock clock;

    public DealSuspicionJob(DealSuspicionService suspicions, ObjectProvider<Clock> clocks) {
        this.suspicions = suspicions;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.HOTDEAL_SUSPICION;
    }

    @Override
    public List<BatchStepDefinition> steps() {
        return List.of(new BatchStepDefinition(STEP_MEMBER, true), new BatchStepDefinition(STEP_STORE, true));
    }

    @Override
    public BatchResult run(BatchContext context) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int opened = 0;
        BatchStep member = context.step(STEP_MEMBER);
        if (member.active()) {
            DealSuspicionService.Tally t = suspicions.detectMembers(now);
            member.record(t.seen(), t.opened(), t.seen() - t.opened());
            opened += t.opened();
        }
        BatchStep store = context.step(STEP_STORE);
        if (store.active()) {
            DealSuspicionService.Tally t = suspicions.detectStores(now);
            store.record(t.seen(), t.opened(), t.seen() - t.opened());
            opened += t.opened();
        }
        return BatchResult.of(opened == 0 ? new MessageRef("batch.result.hotdealSuspicion.none")
                : new MessageRef("batch.result.hotdealSuspicion", String.valueOf(opened)));
    }
}
