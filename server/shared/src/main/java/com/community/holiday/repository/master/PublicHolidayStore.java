package com.community.holiday.repository.master;

import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 한국 공휴일 표({@code public_holidays}, V144 — 날짜 하나에 한 줄, 대체공휴일·선거일 포함). 시드는 공식 자료로 확인한 해만 넣었고 <b>해마다 다음 해를 새 마이그레이션으로 더해야 한다</b>
 * (emoticon-contract 「후속 결정 반영(2026-11-05)」). 읽기만 한다 — 쓰기는 마이그레이션.
 *
 * <p>JDBC 인 이유: 작은 표를 통째로 읽어 값 객체({@code BusinessDayCalendar})로 넘긴다 — 시험이 스프링 문맥 없이 손으로 조립한다(commerce.md 18절).
 */
@Repository
public class PublicHolidayStore {

    private final JdbcTemplate jdbc;

    public PublicHolidayStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 모든 공휴일(날짜 차례) */
    public List<java.time.LocalDate> all() {
        return jdbc.query("SELECT holiday_date FROM public_holidays ORDER BY holiday_date", (rs, i) -> rs.getDate(1).toLocalDate());
    }
}
