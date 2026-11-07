package com.community.payment.repository.master;

import static com.community.payment.repository.master.PaymentOrderStore.cut;

import com.community.payment.entity.PaymentWebhookLogEntity;
import java.time.Instant;
import org.springframework.stereotype.Repository;

/**
 * 웹훅 수신 기록({@code payment_webhook_logs}) — 받은 웹훅 전부를 한 줄씩(결과·HTTP 상태·오류 코드). <b>본문은 저장하지 않는다</b>. 90일 정리는 2a-3 의 DB 배치.
 * 업무와 따로 확정한다(처리가 실패해도 받은 기록은 남는다) — 부르는 쪽이 업무 트랜잭션 밖의 <b>따로 여는 트랜잭션</b>에서 부른다. 읽기는 {@link PaymentAdminQueries}.
 */
@Repository
public class PaymentWebhookLogStore {

    /** 처리 결과(DB CHECK 와 같은 목록) */
    public enum Result {
        PROCESSED, IGNORED, REJECTED, RETRY
    }

    private final PaymentWebhookLogRepository repository;

    public PaymentWebhookLogStore(PaymentWebhookLogRepository repository) {
        this.repository = repository;
    }

    /** 넣고 바로 내보낸다(칸 길이는 여기서 자른다) */
    public void insert(long id, String webhookId, String eventType, String paymentId, Result result, int httpStatus, String errorCode, Instant receivedAt) {
        repository.persistAndFlush(PaymentWebhookLogEntity.of(id, cut(webhookId, 200), cut(eventType, 60), cut(paymentId, 100), result.name(), httpStatus,
                cut(errorCode, 60), receivedAt));
    }
}
