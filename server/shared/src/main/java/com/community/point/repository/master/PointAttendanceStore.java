package com.community.point.repository.master;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 출석 줄({@code point_attendances}, V89) 넣기·읽기와 출석 자격 판정에 필요한 회원 상태 — {@code AttendanceService} 만 부른다. 트랜잭션은 부르는 서비스가 연다.
 *
 * <p>Native SQL(JDBC)인 이유: 넣기는 한 문장 {@code insert … on conflict (user_id, day) do nothing} 이어야 같은 날 두 번(동시에도) 눌러도 한 줄만 생기고 한 번만 성공한다 —
 * 포인트 원장과 같은 트랜잭션에서 영속성 컨텍스트를 거치지 않고 쓴다({@code PointLedgerWriter} 와 같은 이유).
 */
@Repository
public class PointAttendanceStore {

    /** 출석 자격에 필요한 회원 상태 */
    public record MemberState(String status, boolean emailVerified, boolean gone) {

        /** 정지·영구 정지 */
        public boolean suspended() {
            return "SUSPENDED".equals(status) || "BANNED".equals(status);
        }
    }

    private final JdbcTemplate jdbc;

    public PointAttendanceStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public Optional<MemberState> member(long userId) {
        return jdbc.query("select status, email_verified_at is not null, status = 'WITHDRAWN' or deleted_at is not null from users where id = ?",
                (rs, i) -> new MemberState(rs.getString(1), rs.getBoolean(2), rs.getBoolean(3)), userId).stream().findFirst();
    }

    /** 그날 출석 줄을 넣는다 — 이미 있으면(동시에 들어온 것 포함) false */
    public boolean insert(long id, long userId, LocalDate day, int streak, Instant now) {
        return jdbc.update("insert into point_attendances (id, user_id, day, streak, created_at) values (?, ?, ?, ?, ?) on conflict (user_id, day) do nothing", id,
                userId, Date.valueOf(day), streak, Timestamp.from(now)) > 0;
    }

    /** 그날의 연속 일수(그날 출석하지 않았으면 빈 값) */
    public Optional<Integer> streakOn(long userId, LocalDate day) {
        return jdbc.queryForList("select streak from point_attendances where user_id = ? and day = ?", Integer.class, userId, Date.valueOf(day)).stream().findFirst();
    }

    /** {@code from} 이상 {@code toExclusive} 미만의 출석한 날(오름차순) */
    public List<LocalDate> days(long userId, LocalDate from, LocalDate toExclusive) {
        return jdbc.query("select day from point_attendances where user_id = ? and day >= ? and day < ? order by day", (rs, i) -> rs.getDate(1).toLocalDate(), userId,
                Date.valueOf(from), Date.valueOf(toExclusive));
    }

    /** 지금 포인트 합계(잔액 행이 없으면 0) */
    public long balance(long userId) {
        List<Long> rows = jdbc.queryForList("select balance from point_balances where user_id = ?", Long.class, userId);
        return rows.isEmpty() ? 0 : rows.get(0);
    }
}
