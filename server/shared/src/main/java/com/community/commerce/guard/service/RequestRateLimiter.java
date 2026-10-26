package com.community.commerce.guard.service;

import com.community.commerce.guard.dto.RequestAction;
import com.community.commerce.guard.exception.RequestRateLimitedException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * 돈 관련 회원 요청의 속도 제한(docs/domain/commerce.md 12-4) — 회원 × 요청 종류 × <b>1분 고정 창</b>의 시도 수를 DB 로 센다(서버가 여러 대여도 전체 합).
 *
 * <ul>
 *   <li>요청마다 {@code INSERT … ON CONFLICT … DO UPDATE SET count = count + 1 RETURNING count} 한 문장. <b>업무 트랜잭션과 따로</b>(새 트랜잭션으로 먼저) 커밋한다 —
 *       업무가 실패해 되돌아가도 시도 수는 남아 반복 시도를 막는다.</li>
 *   <li>한도({@link RequestRateLimits}, 15초 사본)를 넘으면 429 {@link RequestRateLimitedException} + 지금 창이 끝날 때까지의 초(올림, 최소 1).
 *       한도가 <b>0 이면 제한 없음</b> — 세지도 않는다.</li>
 *   <li>1분 고정 창이라 창 경계에서 잠깐 최대 2배까지 몰릴 수 있다(1차 방어 — 진짜 한도는 도메인 규칙). 지난 창은 매시 DB 배치({@code request-rate-counter-cleanup})가 지운다.</li>
 * </ul>
 * Native SQL 인 이유: {@code INSERT … ON CONFLICT … RETURNING} 은 PostgreSQL 전용이고 표는 UNLOGGED 카운터다(엔티티 없음).
 */
@Component
public class RequestRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RequestRateLimiter.class);

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate separate;
    private final RequestRateLimits limits;
    private final Clock clock;

    @Autowired
    public RequestRateLimiter(DataSource dataSource, PlatformTransactionManager transactions, RequestRateLimits limits) {
        this(dataSource, transactions, limits, Clock.systemUTC());
    }

    public RequestRateLimiter(DataSource dataSource, PlatformTransactionManager transactions, RequestRateLimits limits, Clock clock) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.limits = limits;
        this.clock = clock;
    }

    /**
     * 한 번의 시도를 세고, 한도를 넘었으면 429 를 던진다.
     *
     * @return 이번 창에서 센 시도 수(한도 0 = 제한 없음이면 세지 않고 0)
     */
    public int check(long userId, RequestAction action) {
        int limit = limits.limitOf(action);
        if (limit == 0) {
            return 0;
        }
        Instant now = clock.instant();
        Instant window = now.truncatedTo(ChronoUnit.MINUTES);
        Integer count = separate.execute(status -> jdbc.queryForObject(
                "INSERT INTO request_rate_counters (user_id, action, window_start, count) VALUES (?, ?, ?, 1) "
                        + "ON CONFLICT (user_id, action, window_start) DO UPDATE SET count = request_rate_counters.count + 1 RETURNING count",
                Integer.class, userId, action.name(), Timestamp.from(window)));
        int seen = count == null ? 1 : count;
        if (seen > limit) {
            long retryAfter = retryAfterSeconds(now, window);
            log.info("돈 요청 속도 제한에 걸렸다 userId={} action={} count={} limit={} retryAfter={}s", userId, action, seen, limit, retryAfter);
            throw new RequestRateLimitedException(retryAfter);
        }
        return seen;
    }

    /** 지금 창이 끝날 때까지의 초(올림, 최소 1) */
    static long retryAfterSeconds(Instant now, Instant windowStart) {
        long millis = Duration.between(now, windowStart.plus(WINDOW)).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }
}
