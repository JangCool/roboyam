package com.community.business.repository.master;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 사업자 국세청 진위확인의 회원 × 한국 날짜 카운터({@code business_nts_daily_counts}) — {@code BusinessNtsDailyLimiter} 가 업무와 <b>따로</b>(새 트랜잭션) 부른다.
 *
 * <p>Native SQL 인 이유: {@code INSERT … ON CONFLICT … DO UPDATE … WHERE count < 한도 RETURNING count} 한 문장이라야 서버 여러 대가 동시에 세도 한도를 넘지 않고, 표는 엔티티가 없는
 * 카운터다({@code RequestRateCounterQueries} 와 같다). 지난 날짜 줄은 DB 배치 {@code business-nts-count-cleanup}(R__batch_34)이 지운다.
 */
@Repository
public class BusinessNtsDailyCountQueries {

    private static final String COUNT = "INSERT INTO business_nts_daily_counts (user_id, day, count) VALUES (?, ?, 1) "
            + "ON CONFLICT (user_id, day) DO UPDATE SET count = business_nts_daily_counts.count + 1 WHERE business_nts_daily_counts.count < ? "
            + "RETURNING count";

    private final JdbcTemplate jdbc;

    public BusinessNtsDailyCountQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 한도 아래면 하나 올린 뒤의 수, 이미 한도면 비었다(올리지 않는다) */
    public Optional<Integer> incrementBelow(long userId, LocalDate day, int limit) {
        List<Integer> counted = jdbc.queryForList(COUNT, Integer.class, userId, day, limit);
        return counted.isEmpty() ? Optional.empty() : Optional.ofNullable(counted.getFirst());
    }
}
