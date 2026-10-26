package com.community.payment.repository.master;

import static com.community.payment.repository.master.PaymentOrderStore.cut;
import static com.community.payment.repository.master.PaymentOrderStore.ts;

import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 웹훅 수신 기록({@code payment_webhook_logs}) — 받은 웹훅 전부를 한 줄씩(결과·HTTP 상태·오류 코드). <b>본문은 저장하지 않는다</b>. 90일 정리는 2a-3 의 DB 배치.
 * 업무와 따로 확정한다(처리가 실패해도 받은 기록은 남는다) — 부르는 쪽이 트랜잭션 밖에서 부른다.
 */
@Repository
public class PaymentWebhookLogStore {

    /** 처리 결과(DB CHECK 와 같은 목록) */
    public enum Result {
        PROCESSED, IGNORED, REJECTED, RETRY
    }

    private final JdbcTemplate jdbc;

    public PaymentWebhookLogStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void insert(long id, String webhookId, String eventType, String paymentId, Result result, int httpStatus, String errorCode, Instant receivedAt) {
        jdbc.update("INSERT INTO payment_webhook_logs (id, webhook_id, event_type, payment_id, result, http_status, error_code, received_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, cut(webhookId, 200), cut(eventType, 60), cut(paymentId, 100), result.name(), httpStatus, cut(errorCode, 60), ts(receivedAt));
    }
}
