package com.community.payment.batch;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchItemException;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchStep;
import com.community.batch.core.BatchStepDefinition;
import com.community.batch.core.ChunkProcessor;
import com.community.batch.core.ChunkSource;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.exception.PaymentGatewayUnavailableException;
import com.community.payment.exception.PaymentNotConfiguredException;
import com.community.payment.repository.master.PaymentOrderStore;
import com.community.payment.repository.master.PaymentOrderStore.DueReady;
import com.community.payment.service.PaymentConfirmService;
import com.community.payment.service.PaymentConfirmService.ConfirmResult;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 미확정(READY) 결제 정리 Java 배치 {@code payment-ready-cleanup}(commerce.md 8-2 의 7·15-5 「10분」, 계약 「확정 흐름」).
 *
 * <ul>
 *   <li>대상: 유효 시각 + 여유 시간({@code batch_params} {@code grace_minutes}, 기본 10분 · 0~1440)이 지난 READY <b>전부</b> — 「그날 몫」이 아니라 「아직 처리 안 한 것 전부」.</li>
 *   <li>건마다 포트원 조회 → PAID 면 확정 코드({@link PaymentConfirmService#confirm} — 늦은 결제 판정은 결정 4: READY 면 포트원 결제 시각이 유효 시각 뒤일 때만 늦음),
 *       PAID 가 아니면(대기·실패·없음) EXPIRED. 포트원 장애는 그 건만 실패(건별 실패 기록 {@code PAYMENT_ORDER}) — 다음 실행이 다시 집는다.</li>
 *   <li>확정·만료는 건마다 새 트랜잭션으로 확정된다 — 덩어리가 실패해 한 건씩 다시 돌아도 이미 끝낸 건은 상태가 바뀌어 「건너뜀」이다(멱등).</li>
 * </ul>
 */
@Component
public class PaymentReadyCleanupJob implements BatchJob {

    /** 실패 건의 대상 종류 — 결제 주문 */
    public static final String TARGET = "PAYMENT_ORDER";
    /** 여유 시간 설정값({@code batch_params}) */
    public static final String PARAM_GRACE = "grace_minutes";
    static final long DEFAULT_GRACE_MINUTES = 10;

    private final PaymentOrderStore orders;
    private final PaymentConfirmService confirm;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PaymentReadyCleanupJob(PaymentOrderStore orders, PaymentConfirmService confirm, DataSource dataSource, ObjectProvider<Clock> clocks) {
        this.orders = orders;
        this.confirm = confirm;
        this.jdbc = new JdbcTemplate(dataSource);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.PAYMENT_READY_CLEANUP;
    }

    @Override
    public BatchResult run(BatchContext context) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant cutoff = now.minus(grace());
        BatchStep step = context.step(BatchStepDefinition.MAIN);
        step.chunks(source(cutoff), processor());
        long handled = step.processedCount();
        return BatchResult.of(handled == 0 ? new MessageRef("batch.result.paymentReadyCleanup.none")
                : new MessageRef("batch.result.paymentReadyCleanup", String.valueOf(handled)));
    }

    /** 여유 시간 — {@code batch_param()} 이 범위 밖 값을 기본값으로 바꾼다 */
    Duration grace() {
        Long minutes = jdbc.queryForObject("SELECT batch_param(?, ?, ?, 0, 1440)", Long.class, definition().key(), PARAM_GRACE, DEFAULT_GRACE_MINUTES);
        return Duration.ofMinutes(minutes == null ? DEFAULT_GRACE_MINUTES : minutes);
    }

    ChunkSource<DueReady> source(Instant cutoff) {
        return new ChunkSource<>() {
            @Override
            public String targetType() {
                return TARGET;
            }

            @Override
            public List<DueReady> next(String afterKey, int size) {
                if (afterKey == null) {
                    return orders.dueReady(cutoff, null, 0, size);
                }
                int colon = afterKey.indexOf(':');
                long micros = Long.parseLong(afterKey.substring(0, colon));
                Instant after = Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L), Math.floorMod(micros, 1_000_000L) * 1_000L);
                return orders.dueReady(cutoff, after, Long.parseLong(afterKey.substring(colon + 1)), size);
            }

            @Override
            public List<DueReady> byIds(List<String> targetIds) {
                return orders.dueReadyByIds(cutoff, targetIds.stream().map(Long::valueOf).toList());
            }

            @Override
            public String keyOf(DueReady item) {
                Instant at = item.expiresAt();
                return Math.addExact(Math.multiplyExact(at.getEpochSecond(), 1_000_000L), at.getNano() / 1_000L) + ":" + item.id();
            }

            @Override
            public String targetIdOf(DueReady item) {
                return Long.toString(item.id());
            }
        };
    }

    /** 건마다 확정 코드 → PAID 가 아니면 EXPIRED. 처리한 건수(이미 다른 길이 바꾼 건은 건너뜀) */
    ChunkProcessor<DueReady> processor() {
        return items -> {
            long handled = 0;
            for (DueReady item : items) {
                ConfirmResult result;
                try {
                    result = confirm.confirm(item.paymentId(), ConfirmedVia.READY_CLEANUP);
                } catch (PaymentGatewayUnavailableException | PaymentNotConfiguredException e) {
                    throw new BatchItemException(e.getErrorCode(), "포트원 조회 실패", e);
                }
                handled += switch (result.outcome()) {
                    case ACCEPTED, REJECTED -> 1;
                    case NOT_PAID -> confirm.expireUnpaid(item.id(), "NOT_PAID") ? 1 : 0;
                    case ALREADY_DONE, NOT_FOUND -> 0;
                };
            }
            return handled;
        };
    }
}
