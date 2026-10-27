package com.community.payment.batch;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchItemException;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchParamDefinition;
import com.community.batch.core.BatchParamType;
import com.community.batch.core.BatchParameters;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchStep;
import com.community.batch.core.BatchStepDefinition;
import com.community.batch.core.ChunkProcessor;
import com.community.batch.core.ChunkSource;
import com.community.payment.exception.PaymentGatewayUnavailableException;
import com.community.payment.exception.PaymentNotConfiguredException;
import com.community.payment.repository.master.PaymentReconcileDayStore;
import com.community.payment.service.ReconciliationService;
import com.community.payment.service.ReconciliationService.DayResult;
import com.community.zconfiguration.message.MessageRef;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * PG 대사 Java 배치 {@code payment-reconciliation}(commerce.md 15-5·16-1, 계약 결정 16) — 매일 04:50 KST.
 *
 * <ul>
 *   <li>대상: 파라미터 {@code day}(선택, 어제까지)가 있으면 그 날 하나(「이 날 다시 대사」), 없으면 <b>아직 끝나지 않은 날 전부</b> — 처음 결제가 생긴 날부터 어제까지 중
 *       DONE·MISMATCH 가 아닌 날(15-3 규칙 ② 「마지막으로 대사한 날 다음부터 어제까지」 — 실패한 날·놓친 날도 메꾼다).</li>
 *   <li>날 하나 = 한 건. 포트원 장애는 그 날만 실패(건별 실패 기록 {@code PAYMENT_RECONCILE_DAY}, 날짜 상태 FAILED) — 다음 실행·「실패 건만 다시」가 다시 한다.
 *       체크포인트는 날짜라 「실패 지점부터 다시」가 그 다음 날부터 잇는다(이어 받기).</li>
 *   <li>자동 보정 없음 — 이슈만 남긴다({@link ReconciliationService}). 새 이슈가 있으면 끝에 운영자 알림 하나({@code COMMERCE_RECONCILE_MISMATCH}, 하루 한 번).</li>
 * </ul>
 */
@Component
public class PaymentReconciliationJob implements BatchJob {

    /** 실패 건의 대상 종류 — 대사한 날 */
    public static final String TARGET = "PAYMENT_RECONCILE_DAY";
    /** 대사할 날(KST, 어제까지) */
    public static final String PARAM_DAY = "day";

    private final ReconciliationService reconciliation;
    private final PaymentReconcileDayStore days;

    public PaymentReconciliationJob(ReconciliationService reconciliation, PaymentReconcileDayStore days) {
        this.reconciliation = reconciliation;
        this.days = days;
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.PAYMENT_RECONCILIATION;
    }

    @Override
    public List<BatchParamDefinition> params() {
        return List.of(new BatchParamDefinition(PARAM_DAY, BatchParamType.DATE, false));
    }

    @Override
    public boolean resumable() {
        return true;
    }

    @Override
    public Map<String, MessageRef> validate(BatchParameters parameters) {
        return parameters.date(PARAM_DAY).filter(day -> day.isAfter(reconciliation.yesterday()))
                .map(day -> Map.of(PARAM_DAY, new MessageRef("payment.reconcile.day.notPast")))
                .orElse(Map.of());
    }

    @Override
    public BatchResult run(BatchContext context) {
        LocalDate only = context.parameters().date(PARAM_DAY).orElse(null);
        AtomicInteger opened = new AtomicInteger();
        BatchStep step = context.step(BatchStepDefinition.MAIN);
        step.chunks(source(only), processor(context.runId(), opened));
        reconciliation.notifyMismatches(opened.get());
        long handled = step.processedCount();
        return BatchResult.of(handled == 0 ? new MessageRef("batch.result.paymentReconciliation.none")
                : new MessageRef("batch.result.paymentReconciliation", String.valueOf(handled), String.valueOf(opened.get())));
    }

    ChunkSource<LocalDate> source(LocalDate only) {
        return new ChunkSource<>() {
            @Override
            public String targetType() {
                return TARGET;
            }

            @Override
            public List<LocalDate> next(String afterKey, int size) {
                LocalDate after = afterKey == null ? null : LocalDate.parse(afterKey);
                if (only != null) {
                    return after == null || after.isBefore(only) ? List.of(only) : List.of();
                }
                return days.unfinished(reconciliation.firstDay(), reconciliation.yesterday(), after, size);
            }

            @Override
            public List<LocalDate> byIds(List<String> targetIds) {
                return targetIds.stream().map(LocalDate::parse).sorted().toList();
            }

            @Override
            public String keyOf(LocalDate item) {
                return item.toString();
            }

            @Override
            public String targetIdOf(LocalDate item) {
                return item.toString();
            }
        };
    }

    /** 날마다 대사 — 처리한 날 수. 새로 연 이슈 수는 {@code opened} 에 더한다 */
    ChunkProcessor<LocalDate> processor(long runId, AtomicInteger opened) {
        return items -> {
            long handled = 0;
            for (LocalDate day : items) {
                DayResult result;
                try {
                    result = reconciliation.reconcileDay(day, runId);
                } catch (PaymentGatewayUnavailableException | PaymentNotConfiguredException e) {
                    throw new BatchItemException(e.getErrorCode(), "포트원 조회 실패", e);
                }
                opened.addAndGet(result.opened());
                handled++;
            }
            return handled;
        };
    }
}
