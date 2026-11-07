package com.community.commerce.guard.repository.master;

import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 돈 요청 속도 제한의 1분 창 카운터({@code request_rate_counters}, UNLOGGED) — {@code RequestRateLimiter} 가 업무와 <b>따로</b>(새 트랜잭션) 부른다. 한도 표는
 * {@link RequestRateLimitRepositoryQDSL} 이다.
 *
 * <p>Native SQL 인 이유: {@code INSERT … ON CONFLICT … DO UPDATE … RETURNING count} 한 문장이라야 서버 여러 대가 동시에 세도 한 줄에 정확히 더해지고, 표는 엔티티가 없는 카운터다.
 */
@Repository
public class RequestRateCounterQueries {

    private final JdbcTemplate jdbc;

    public RequestRateCounterQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 이 창의 시도 하나를 더하고 더한 뒤의 수(돌려받지 못하면 null) */
    public Integer increment(long userId, String action, Instant windowStart) {
        return jdbc.queryForObject("INSERT INTO request_rate_counters (user_id, action, window_start, count) VALUES (?, ?, ?, 1) "
                        + "ON CONFLICT (user_id, action, window_start) DO UPDATE SET count = request_rate_counters.count + 1 RETURNING count",
                Integer.class, userId, action, Timestamp.from(windowStart));
    }
}
