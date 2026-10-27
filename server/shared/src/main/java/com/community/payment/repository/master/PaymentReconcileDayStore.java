package com.community.payment.repository.master;

import static com.community.payment.repository.master.PaymentOrderStore.instant;
import static com.community.payment.repository.master.PaymentOrderStore.ts;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 대사한 날({@code payment_reconcile_days}, KST 날짜) — 「마지막으로 대사한 날 다음부터 어제까지」(commerce.md 15-3 규칙 ②)의 기록. 줄이 없는 날은 「아직 안 함」이다.
 * 상태: RUNNING(도는 중) · DONE(불일치 없음) · MISMATCH(불일치 있음) · FAILED(포트원 장애 등 — 다음 실행이 다시 한다).
 *
 * <p>Native SQL(JDBC)인 이유: {@code INSERT … ON CONFLICT DO UPDATE}(같은 날을 다시 대사하면 줄을 덮어쓴다)와 {@code generate_series}(아직 안 한 날 찾기)를 쓴다.
 */
@Repository
public class PaymentReconcileDayStore {

    /** 날짜 상태(DB CHECK 와 같은 목록 — 「아직 안 함」은 줄이 없다) */
    public enum Status {
        RUNNING, DONE, MISMATCH, FAILED
    }

    /** 날짜 한 줄 */
    public record DayRow(LocalDate day, Status status, int checkedCount, int issueCount, Long runId, Instant finishedAt) {
    }

    private final JdbcTemplate jdbc;

    public PaymentReconcileDayStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 그 날을 도는 중으로 둔다(건수는 이전 값 그대로) */
    public void markRunning(LocalDate day, Long runId) {
        jdbc.update("INSERT INTO payment_reconcile_days (day, status, run_id) VALUES (?, 'RUNNING', ?) "
                + "ON CONFLICT (day) DO UPDATE SET status = 'RUNNING', run_id = excluded.run_id", Date.valueOf(day), runId);
    }

    /** 그 날의 결과를 남긴다 */
    public void finish(LocalDate day, Status status, int checkedCount, int issueCount, Long runId, Instant now) {
        jdbc.update("INSERT INTO payment_reconcile_days (day, status, checked_count, issue_count, run_id, finished_at) VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (day) DO UPDATE SET status = excluded.status, checked_count = excluded.checked_count, issue_count = excluded.issue_count, "
                        + "run_id = excluded.run_id, finished_at = excluded.finished_at", Date.valueOf(day), status.name(), checkedCount, issueCount, runId, ts(now));
    }

    public Optional<DayRow> find(LocalDate day) {
        return jdbc.query("SELECT day, status, checked_count, issue_count, run_id, finished_at FROM payment_reconcile_days WHERE day = ?", PaymentReconcileDayStore::row,
                Date.valueOf(day)).stream().findFirst();
    }

    /** [from, to] 의 기록이 있는 날(날짜순) */
    public List<DayRow> range(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT day, status, checked_count, issue_count, run_id, finished_at FROM payment_reconcile_days WHERE day BETWEEN ? AND ? ORDER BY day",
                PaymentReconcileDayStore::row, Date.valueOf(from), Date.valueOf(to));
    }

    /** 대사가 끝난(DONE·MISMATCH) 마지막 날 */
    public Optional<LocalDate> lastChecked() {
        Date day = jdbc.queryForObject("SELECT max(day) FROM payment_reconcile_days WHERE status IN ('DONE', 'MISMATCH')", Date.class);
        return Optional.ofNullable(day).map(Date::toLocalDate);
    }

    /**
     * [from, to] 중 아직 끝나지 않은 날(줄이 없거나 RUNNING·FAILED) — 날짜순, 최대 {@code limit} 개, {@code after} 다음부터(이어 받기 체크포인트).
     * 하루를 놓쳐도·실패해도 다음 실행이 메꾼다.
     */
    public List<LocalDate> unfinished(LocalDate from, LocalDate to, LocalDate after, int limit) {
        LocalDate start = after != null && !after.isBefore(from) ? after.plusDays(1) : from;
        if (start.isAfter(to)) {
            return List.of();
        }
        return jdbc.query("SELECT d::date FROM generate_series(?::date, ?::date, interval '1 day') d "
                        + "WHERE NOT EXISTS (SELECT 1 FROM payment_reconcile_days r WHERE r.day = d::date AND r.status IN ('DONE', 'MISMATCH')) ORDER BY d LIMIT ?",
                (rs, i) -> rs.getDate(1).toLocalDate(), Date.valueOf(start), Date.valueOf(to), limit);
    }

    private static DayRow row(ResultSet rs, int i) throws SQLException {
        return new DayRow(rs.getDate("day").toLocalDate(), Status.valueOf(rs.getString("status")), rs.getInt("checked_count"), rs.getInt("issue_count"),
                rs.getObject("run_id") == null ? null : rs.getLong("run_id"), instant(rs.getTimestamp("finished_at")));
    }
}
