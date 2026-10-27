package com.community.payment.service;

import com.community.payment.config.PaymentProperties;
import com.community.payment.repository.master.PaymentCancelStore;
import com.community.payment.repository.master.PaymentCancelStore.CancelRow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * PG 취소 재시도 워커의 한 바퀴(commerce.md 15-4·15-5 「PG 취소 재시도 — Java 워커 1분」) — 「언제 돌릴지」(1분, 배치를 켠 관리자 서버만, 워커 기록 {@value #WORKER_KEY})는
 * 관리자 서버의 {@code PaymentCancelScheduler} 가 정한다(배치 서버로 뗄 때 그 클래스만 옮긴다, 15-2).
 *
 * <ol>
 *   <li>SENDING 으로 {@code roboyam.payment.cancel-stale}(10분) 넘게 머문 줄(보내던 서버가 죽었다)을 PENDING 으로 되돌린다 — 다음 시도의 「재시도 전 조회」가 이중 취소를 막는다.</li>
 *   <li>차례가 된 줄을 {@code FOR UPDATE SKIP LOCKED} 로 {@code cancel-batch-size}(20)줄까지 집는다 — 서버 여럿이 돌아도 같은 취소를 두 번 집지 않는다.</li>
 *   <li>줄마다 {@link PaymentCancelExecutor#execute} — 줄 사이에 {@code cancel-gap}(200ms)을 둔다(PG 호출 속도 제한, commerce.md 8-3).</li>
 * </ol>
 */
@Component
public class PaymentCancelWorker {

    private static final Logger log = LoggerFactory.getLogger(PaymentCancelWorker.class);

    /** 워커 기록({@code worker_heartbeats})의 키 */
    public static final String WORKER_KEY = "payment-cancel";

    /** 건 사이 기다리기(시험은 기다리지 않고 센다) */
    @FunctionalInterface
    public interface Pause {
        void pause(Duration duration) throws InterruptedException;
    }

    private final PaymentCancelStore cancels;
    private final PaymentCancelExecutor executor;
    private final PaymentProperties properties;
    private final Pause pause;
    private final Clock clock;

    @Autowired
    public PaymentCancelWorker(PaymentCancelStore cancels, PaymentCancelExecutor executor, PaymentProperties properties, ObjectProvider<Clock> clocks) {
        this(cancels, executor, properties, duration -> Thread.sleep(duration.toMillis()), clocks.getIfUnique(Clock::systemUTC));
    }

    public PaymentCancelWorker(PaymentCancelStore cancels, PaymentCancelExecutor executor, PaymentProperties properties, Pause pause, Clock clock) {
        this.cancels = cancels;
        this.executor = executor;
        this.properties = properties;
        this.pause = pause;
        this.clock = clock;
    }

    /**
     * 한 바퀴.
     *
     * @return 이번 바퀴에 실행한 줄 수(워커 기록의 건수)
     */
    public int runOnce() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int recovered = cancels.recoverStale(now.minus(properties.cancelStale()), now);
        if (recovered > 0) {
            log.warn("보내던 중 멈춘 취소 대기열 줄을 되돌렸다 count={}", recovered);
        }
        List<CancelRow> claimed = cancels.claimDue(properties.cancelBatchSize(), executor.nodeId(), now);
        for (int i = 0; i < claimed.size(); i++) {
            if (i > 0 && !properties.cancelGap().isZero()) {
                try {
                    pause.pause(properties.cancelGap());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // 남은 줄은 SENDING 으로 두지 않고 지금 실행한다(멈추면 오래 머문 줄이 된다)
                }
            }
            executor.execute(claimed.get(i));
        }
        return claimed.size();
    }
}
