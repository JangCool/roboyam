package com.community.commerce.guard.service;

import com.community.commerce.guard.dto.RequestAction;
import com.community.commerce.guard.dto.StoredResponse;
import com.community.commerce.guard.exception.IdempotencyInProgressException;
import com.community.commerce.guard.exception.IdempotencyKeyInvalidException;
import com.community.commerce.guard.exception.IdempotencyKeyReusedException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 돈이 움직이는 회원 요청의 클라이언트 멱등 키({@code Idempotency-Key}, docs/domain/commerce.md 3절) — 같은 키·같은 본문으로 다시 오면 업무를 다시 하지 않고 처음 응답을 준다.
 *
 * <ol>
 *   <li><b>처리 중 행을 따로 먼저 저장</b>한다(새 트랜잭션, {@code INSERT … ON CONFLICT DO NOTHING}). 이미 있으면 —
 *       본문 해시가 다르면 409 {@link IdempotencyKeyReusedException}, 아직 처리 중이면 409 {@link IdempotencyInProgressException}, 끝났으면 저장한 응답을 다시 준다.
 *       {@value #TTL_HOURS}시간이 지난 행은 없는 것으로 보고 새로 시작한다(정리는 DB 배치 {@code idempotency-request-cleanup}).</li>
 *   <li>업무를 <b>업무 트랜잭션 안에서</b> 하고, 같은 트랜잭션에서 결과(상태·응답)를 행에 쓴다 — 업무가 커밋되면 응답도 함께 남고, 롤백되면 함께 사라진다.</li>
 *   <li>업무가 실패하면(예외) 처리 중 행을 <b>지운다</b>(새 트랜잭션) — 같은 키로 다시 시도할 수 있다. 예외는 그대로 다시 던진다.</li>
 * </ol>
 * 응답 본문은 {@value StoredResponse#MAX_BODY_BYTES} 바이트까지 저장한다 — 넘으면 프로그램 결함으로 보고 업무를 되돌린다(돈 요청의 응답은 번호·상태 정도로 작다).
 * Native SQL 인 이유: {@code ON CONFLICT}·업무 트랜잭션과 따로 커밋하는 짧은 쓰기라 JPA 영속성 문맥에 엮지 않는다.
 */
@Component
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    /** 멱등 키를 기억하는 시간 */
    public static final int TTL_HOURS = 24;
    private static final Duration TTL = Duration.ofHours(TTL_HOURS);

    /** 키 형식 — 영문·숫자·밑줄·하이픈 8~64자(UUID 가 들어간다). DB CHECK 와 같다 */
    public static final Pattern KEY_FORMAT = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate separate;
    private final TransactionTemplate business;
    private final Clock clock;

    @Autowired
    public IdempotencyService(DataSource dataSource, PlatformTransactionManager transactions) {
        this(dataSource, transactions, Clock.systemUTC());
    }

    public IdempotencyService(DataSource dataSource, PlatformTransactionManager transactions, Clock clock) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.business = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /**
     * 멱등 키로 감싸 업무를 한다.
     *
     * @param requestHash 요청 종류 + 정렬한 본문의 SHA-256(16진 64자 — {@link RequestHashes})
     * @param work        업무 — 이 메서드가 연 업무 트랜잭션 안에서 불린다(업무 서비스의 {@code @Transactional} 은 여기에 합류한다). 돌려준 응답을 저장한다
     * @return 처음 처리한 응답 또는 저장해 둔 응답({@link StoredResponse#replayed()})
     */
    public StoredResponse execute(long userId, String key, RequestAction action, String requestHash, Supplier<StoredResponse> work) {
        if (key == null || !KEY_FORMAT.matcher(key).matches()) {
            throw new IdempotencyKeyInvalidException();
        }
        StoredResponse replay = separate.execute(status -> begin(userId, key, action, requestHash));
        if (replay != null) {
            log.info("멱등 키로 처음 응답을 다시 줬다 userId={} action={}", userId, action);
            return replay;
        }
        try {
            return business.execute(status -> {
                StoredResponse response = work.get();
                complete(userId, key, response);
                return StoredResponse.fresh(response.status(), response.body());
            });
        } catch (RuntimeException | Error e) {
            abandon(userId, key);
            throw e;
        }
    }

    /** 처리 중 행을 만든다 — 만들었으면 null, 이미 끝난 같은 요청이면 저장한 응답 */
    private StoredResponse begin(long userId, String key, RequestAction action, String requestHash) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        jdbc.update("DELETE FROM idempotency_requests WHERE user_id = ? AND idem_key = ? AND created_at < ?", userId, key, Timestamp.from(now.minus(TTL)));
        int inserted = jdbc.update("INSERT INTO idempotency_requests (user_id, idem_key, action, request_hash, status, created_at) VALUES (?, ?, ?, ?, 'IN_PROGRESS', ?) "
                + "ON CONFLICT (user_id, idem_key) DO NOTHING", userId, key, action.name(), requestHash, Timestamp.from(now));
        if (inserted == 1) {
            return null;
        }
        List<Existing> rows = jdbc.query("SELECT action, request_hash, status, response_code, response_body FROM idempotency_requests WHERE user_id = ? AND idem_key = ?",
                (rs, i) -> new Existing(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5)), userId, key);
        if (rows.isEmpty()) {
            // 그 사이 다른 요청이 실패해 행을 지웠다 — 같은 키로 다시 시도하게 처리 중으로 본다(클라이언트가 다시 보낸다)
            throw new IdempotencyInProgressException();
        }
        Existing row = rows.get(0);
        if (!row.action().equals(action.name()) || !row.requestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        if (!"COMPLETED".equals(row.status())) {
            throw new IdempotencyInProgressException();
        }
        return new StoredResponse(row.responseCode(), row.responseBody(), true);
    }

    /** 업무 트랜잭션 안에서 결과를 쓴다 */
    private void complete(long userId, String key, StoredResponse response) {
        if (response == null) {
            throw new IllegalStateException("멱등 요청의 업무가 응답을 돌려주지 않았다");
        }
        if (response.body() != null && response.body().getBytes(StandardCharsets.UTF_8).length > StoredResponse.MAX_BODY_BYTES) {
            throw new IllegalStateException("멱등 요청의 응답이 저장 한도(" + StoredResponse.MAX_BODY_BYTES + "바이트)보다 크다");
        }
        int updated = jdbc.update("UPDATE idempotency_requests SET status = 'COMPLETED', response_code = ?, response_body = ?, completed_at = ? "
                + "WHERE user_id = ? AND idem_key = ? AND status = 'IN_PROGRESS'", response.status(), response.body(),
                Timestamp.from(clock.instant().truncatedTo(ChronoUnit.MICROS)), userId, key);
        if (updated != 1) {
            throw new IllegalStateException("멱등 요청의 처리 중 행이 없다");
        }
    }

    /** 업무가 실패했다 — 처리 중 행을 지워 같은 키로 다시 할 수 있게 한다. 지우다 실패해도 원래 예외를 덮지 않는다(24시간 뒤 정리) */
    private void abandon(long userId, String key) {
        try {
            separate.executeWithoutResult(status -> jdbc.update("DELETE FROM idempotency_requests WHERE user_id = ? AND idem_key = ? AND status = 'IN_PROGRESS'", userId, key));
        } catch (RuntimeException e) {
            log.warn("실패한 멱등 요청의 처리 중 행을 지우지 못했다 userId={} 원인={}", userId, e.getClass().getSimpleName());
        }
    }

    private record Existing(String action, String requestHash, String status, int responseCode, String responseBody) {
    }
}
