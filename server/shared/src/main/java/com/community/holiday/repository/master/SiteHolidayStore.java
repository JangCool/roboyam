package com.community.holiday.repository.master;

import com.community.holiday.entity.HolidaySource;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 사이트 쉬는 날 표({@code site_holidays}, shared V150 — 출처 공공데이터·기본값·회사 휴무일, 같은 날 여러 줄) 와 받기 결과({@code site_holiday_sync}, 1행) 읽기·쓰기.
 *
 * <p>JDBC 인 이유: 작은 표를 통째로(또는 해 단위로) 읽어 값 객체({@code BusinessDayCalendar})로 넘기고, 해 하나를 맞추는 일(넣기·지우기·이름 바꾸기)을 문장 몇 개로 끝낸다.
 * 시험이 스프링 문맥 없이 손으로 조립한다(commerce.md 18절). 트랜잭션은 부르는 서비스의 것을 따른다.
 */
@Repository
public class SiteHolidayStore {

    /** 쉬는 날 전부(날짜만, 겹침 없이) — 공구 설정 읽기({@code GroupBuyStore#settings})도 이 글자를 같이 쓴다 */
    public static final String ALL_DATES_SQL = "SELECT DISTINCT holiday_date FROM site_holidays ORDER BY holiday_date";

    /**
     * 한 줄.
     *
     * @param createdByName 넣은 운영자 이름(공공데이터·기본값 · 옮긴 줄 · 지워진 운영자는 null)
     */
    public record Row(long id, LocalDate date, String name, HolidaySource source, boolean substitute, String createdByName, Instant createdAt) {
    }

    /** 받기 결과(1행) */
    public record SyncRow(Instant ranAt, String outcome, List<Integer> appliedYears, List<Integer> failedYears, int changed, String ranByName) {
    }

    private static final String SELECT = "SELECT h.id, h.holiday_date, h.name, h.source, h.substitute, o.operator_name, h.created_at FROM site_holidays h "
            + "LEFT JOIN operators o ON o.id = h.created_by ";
    private static final String ORDER = " ORDER BY h.holiday_date, CASE h.source WHEN 'OPERATOR' THEN 1 ELSE 0 END, h.id";

    private final JdbcTemplate jdbc;

    public SiteHolidayStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 모든 쉬는 날(날짜 차례, 겹침 없이) */
    public List<LocalDate> allDates() {
        return jdbc.query(ALL_DATES_SQL, (rs, i) -> rs.getDate(1).toLocalDate());
    }

    /** {@code from}~{@code to}(양 끝 포함)의 줄 — 날짜 차례 */
    public List<Row> between(LocalDate from, LocalDate to) {
        return jdbc.query(SELECT + "WHERE h.holiday_date BETWEEN ? AND ?" + ORDER, SiteHolidayStore::row, from, to);
    }

    /** 그 해의 한 출처 줄 */
    public List<Row> ofYear(int year, HolidaySource source) {
        return jdbc.query(SELECT + "WHERE h.source = ? AND h.holiday_date BETWEEN ? AND ?" + ORDER, SiteHolidayStore::row, source.name(), LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31));
    }

    public Optional<Row> find(long id) {
        return jdbc.query(SELECT + "WHERE h.id = ?", SiteHolidayStore::row, id).stream().findFirst();
    }

    /** 그날 회사 휴무일이 있는가 */
    public boolean operatorDayExists(LocalDate date) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM site_holidays WHERE source = 'OPERATOR' AND holiday_date = ?)", Boolean.class, date);
        return Boolean.TRUE.equals(exists);
    }

    /** 새 줄 — 만든 ID */
    public long insert(LocalDate date, String name, HolidaySource source, boolean substitute, Long createdBy, Instant now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(c -> {
            var ps = c.prepareStatement("INSERT INTO site_holidays (holiday_date, name, source, substitute, created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    new String[] {"id"});
            ps.setObject(1, date);
            ps.setString(2, name);
            ps.setString(3, source.name());
            ps.setBoolean(4, substitute);
            ps.setObject(5, createdBy);
            ps.setObject(6, ts(now));
            ps.setObject(7, ts(now));
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    /** 이름·대체공휴일 여부 바꾸기 */
    public void rename(long id, String name, boolean substitute, Instant now) {
        jdbc.update("UPDATE site_holidays SET name = ?, substitute = ?, updated_at = ? WHERE id = ?", name, substitute, ts(now), id);
    }

    public boolean delete(long id) {
        return jdbc.update("DELETE FROM site_holidays WHERE id = ?", id) == 1;
    }

    /** 그 해의 기본값(SEED) 줄 지우기 — 지운 수 */
    public int deleteSeedOfYear(int year) {
        return jdbc.update("DELETE FROM site_holidays WHERE source = 'SEED' AND holiday_date BETWEEN ? AND ?", LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    /** 받기 결과 남기기(1행을 덮어쓴다) */
    public void recordSync(Instant ranAt, String outcome, List<Integer> appliedYears, List<Integer> failedYears, int changed, Long ranBy) {
        jdbc.update("INSERT INTO site_holiday_sync (id, ran_at, outcome, applied_years, failed_years, changed, ran_by) VALUES (1, ?, ?, ?::integer[], ?::integer[], ?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET ran_at = EXCLUDED.ran_at, outcome = EXCLUDED.outcome, applied_years = EXCLUDED.applied_years, "
                        + "failed_years = EXCLUDED.failed_years, changed = EXCLUDED.changed, ran_by = EXCLUDED.ran_by",
                ts(ranAt), outcome, array(appliedYears), array(failedYears), changed, ranBy);
    }

    public Optional<SyncRow> lastSync() {
        return jdbc.query("SELECT s.ran_at, s.outcome, s.applied_years, s.failed_years, s.changed, o.operator_name FROM site_holiday_sync s "
                        + "LEFT JOIN operators o ON o.id = s.ran_by WHERE s.id = 1",
                (rs, i) -> new SyncRow(rs.getTimestamp(1).toInstant(), rs.getString(2), ints(rs.getArray(3)), ints(rs.getArray(4)), rs.getInt(5), rs.getString(6)))
                .stream().findFirst();
    }

    private static Row row(ResultSet rs, int i) throws SQLException {
        Timestamp at = rs.getTimestamp(7);
        return new Row(rs.getLong(1), rs.getDate(2).toLocalDate(), rs.getString(3), HolidaySource.valueOf(rs.getString(4)), rs.getBoolean(5), rs.getString(6),
                at == null ? null : at.toInstant());
    }

    private static List<Integer> ints(Array array) throws SQLException {
        List<Integer> out = new ArrayList<>();
        if (array != null) {
            for (Object v : (Object[]) array.getArray()) {
                out.add(((Number) v).intValue());
            }
        }
        return out;
    }

    private static String array(List<Integer> values) {
        return values.toString().replace('[', '{').replace(']', '}').replace(" ", "");
    }

    private static OffsetDateTime ts(Instant at) {
        return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
