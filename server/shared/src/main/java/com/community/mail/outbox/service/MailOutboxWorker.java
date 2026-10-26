package com.community.mail.outbox.service;

import com.community.batch.core.BatchErrorText;
import com.community.mail.outbox.dto.MailOutboxPolicy;
import com.community.mail.outbox.dto.OutboxDelivery;
import com.community.mail.outbox.repository.master.MailOutboxStore;
import com.community.mail.outbox.repository.master.MailOutboxStore.Claimed;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 커머스 메일 대기열 발송 워커의 한 바퀴(docs/domain/commerce.md 14절·15-4) — 「언제 돌릴지」(30초, 배치를 켠 관리자 서버만, 워커 기록)는 관리자 서버의 {@code MailOutboxScheduler} 가 정한다.
 *
 * <ol>
 *   <li>SENDING 으로 {@link MailOutboxPolicy#staleAfter()} 넘게 머문 줄(보내던 서버가 죽었다)을 되돌린다 — 시도 한도면 DEAD, 아니면 PENDING.
 *       그 줄은 이미 나갔을 수도 있어 한 번 더 갈 수 있다(적어도 한 번 — 받아들임).</li>
 *   <li>보낼 차례인 줄을 {@code FOR UPDATE SKIP LOCKED} 로 집어 SENDING 으로 커밋한다 — 서버 여럿이 돌아도 같은 줄을 두 번 집지 않는다.</li>
 *   <li>줄마다 <b>트랜잭션 밖에서</b> 보낸다({@link OutboxMailSender}) — 메일 서버가 느려도 DB 를 붙잡지 않는다.
 *       보냄 → SENT(본문 지움·가린 주소) · 받을 수 없음 → CANCELED · 메일 서버 미설정이나 오류 → 재시도(1·2·4…분, 최대 2시간 간격) · 시도 한도 → DEAD.</li>
 * </ol>
 * 오류 문구는 {@link BatchErrorText}(이메일·비밀값 가림)로 줄여 남기고, 로그에는 예외 클래스 이름만 남긴다.
 */
@Component
public class MailOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(MailOutboxWorker.class);

    /** 워커 기록({@code worker_heartbeats})의 키 */
    public static final String WORKER_KEY = "mail-outbox";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> VARIABLES = new TypeReference<>() {
    };

    private final MailOutboxStore store;
    private final OutboxMailSender sender;
    private final MailOutboxPolicy policy;
    private final int nodeId;
    private final Clock clock;

    @Autowired
    public MailOutboxWorker(MailOutboxStore store, OutboxMailSender sender, @Value("${roboyam.node-id}") int nodeId,
                            @Value("${roboyam.mail-outbox.max-attempts:12}") int maxAttempts, @Value("${roboyam.mail-outbox.batch-size:20}") int batchSize,
                            @Value("${roboyam.mail-outbox.stale-minutes:10}") long staleMinutes) {
        this(store, sender, new MailOutboxPolicy(maxAttempts, batchSize, Duration.ofMinutes(staleMinutes), MailOutboxPolicy.DEFAULTS.baseBackoff(),
                MailOutboxPolicy.DEFAULTS.maxBackoff()), nodeId, Clock.systemUTC());
    }

    public MailOutboxWorker(MailOutboxStore store, OutboxMailSender sender, MailOutboxPolicy policy, int nodeId, Clock clock) {
        this.store = store;
        this.sender = sender;
        this.policy = policy;
        this.nodeId = nodeId;
        this.clock = clock;
    }

    /**
     * 한 바퀴.
     *
     * @return 이번 바퀴에 끝낸 줄 수(보냄·재시도·끝냄 — 워커 기록의 건수)
     */
    public int runOnce() {
        Instant now = now();
        int recovered = store.recoverStale(now.minus(policy.staleAfter()), policy.maxAttempts(), now);
        if (recovered > 0) {
            log.warn("보내던 중 멈춘 메일 대기열 줄을 되돌렸다 count={}", recovered);
        }
        List<Claimed> claimed = store.claim(policy.batchSize(), nodeId, now);
        for (Claimed row : claimed) {
            deliver(row);
        }
        return claimed.size();
    }

    private void deliver(Claimed row) {
        OutboxDelivery delivery;
        try {
            Map<String, Object> variables = row.variablesJson() == null ? Map.of() : JSON.readValue(row.variablesJson(), VARIABLES);
            delivery = sender.send(row.userId(), row.kind(), variables);
        } catch (RuntimeException e) {
            log.warn("대기열 메일을 보내지 못했다 id={} kind={} attempts={} 원인={}", row.id(), row.kind(), row.attempts(), e.getClass().getSimpleName());
            retryOrDead(row, BatchErrorText.of(e));
            return;
        }
        switch (delivery.outcome()) {
            case SENT -> {
                if (store.markSent(row.id(), nodeId, delivery.toMasked(), now())) {
                    log.info("대기열 메일을 보냈다 id={} kind={} attempts={}", row.id(), row.kind(), row.attempts());
                }
            }
            case CANCELED -> {
                store.markFinished(row.id(), nodeId, "CANCELED", delivery.reason(), now());
                log.info("대기열 메일을 보내지 않기로 했다 id={} kind={} 까닭={}", row.id(), row.kind(), delivery.reason());
            }
            case RETRY -> retryOrDead(row, delivery.reason());
        }
    }

    private void retryOrDead(Claimed row, String error) {
        Instant now = now();
        if (row.attempts() >= policy.maxAttempts()) {
            store.markFinished(row.id(), nodeId, "DEAD", error, now);
            log.error("대기열 메일을 시도 한도({})까지 보내지 못해 그만뒀다 id={} kind={}", policy.maxAttempts(), row.id(), row.kind());
            return;
        }
        store.markRetry(row.id(), nodeId, error, now.plus(policy.backoffAfter(row.attempts())));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
