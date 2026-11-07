package com.community.commerce.guard.repository.master;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 돈 요청의 멱등 키 행({@code idempotency_requests}, PK (user_id, idem_key)) — {@code IdempotencyService} 가 처리 중 행 만들기·지우기는 <b>업무와 따로</b>(새 트랜잭션),
 * 결과 쓰기는 <b>업무 트랜잭션 안에서</b> 부른다(트랜잭션은 부르는 서비스가 연다).
 *
 * <p>Native SQL 인 이유: 처리 중 행 만들기가 {@code INSERT … ON CONFLICT DO NOTHING}(동시에 같은 키가 와도 한 줄 — 실패한 문장이 트랜잭션을 망가뜨리지 않게)이고, 표에 엔티티가 없다
 * (업무와 따로 확정하는 짧은 쓰기라 업무의 JPA 영속성 문맥에 엮지 않는다). 나머지 문장도 같은 표의 키 조건 한 줄짜리다.
 */
@Repository
public class IdempotencyRequestQueries {

    /** 저장된 한 줄 */
    public record IdempotencyRow(String action, String requestHash, String status, int responseCode, String responseBody) {
    }

    private final JdbcTemplate jdbc;

    public IdempotencyRequestQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 이 키의 오래된 행(만든 지 {@code before} 이전)을 지운다 — 없는 것으로 보고 새로 시작하게 */
    public void deleteExpired(long userId, String key, Instant before) {
        jdbc.update("DELETE FROM idempotency_requests WHERE user_id = ? AND idem_key = ? AND created_at < ?", userId, key, Timestamp.from(before));
    }

    /** 처리 중 행을 만든다 — 만들었으면 true(이미 있으면 그대로 두고 false) */
    public boolean insertInProgress(long userId, String key, String action, String requestHash, Instant now) {
        return jdbc.update("INSERT INTO idempotency_requests (user_id, idem_key, action, request_hash, status, created_at) VALUES (?, ?, ?, ?, 'IN_PROGRESS', ?) "
                + "ON CONFLICT (user_id, idem_key) DO NOTHING", userId, key, action, requestHash, Timestamp.from(now)) == 1;
    }

    /** 이 키의 행 */
    public Optional<IdempotencyRow> find(long userId, String key) {
        List<IdempotencyRow> rows = jdbc.query("SELECT action, request_hash, status, response_code, response_body FROM idempotency_requests WHERE user_id = ? AND idem_key = ?",
                (rs, i) -> new IdempotencyRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5)), userId, key);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** 처리 중 행에 결과를 쓴다(조건부 — 처리 중일 때만) — 바꿨으면 true */
    public boolean complete(long userId, String key, int responseCode, String responseBody, Instant completedAt) {
        return jdbc.update("UPDATE idempotency_requests SET status = 'COMPLETED', response_code = ?, response_body = ?, completed_at = ? "
                + "WHERE user_id = ? AND idem_key = ? AND status = 'IN_PROGRESS'", responseCode, responseBody, Timestamp.from(completedAt), userId, key) == 1;
    }

    /** 처리 중 행을 지운다(업무가 실패했다 — 같은 키로 다시 할 수 있게) */
    public void deleteInProgress(long userId, String key) {
        jdbc.update("DELETE FROM idempotency_requests WHERE user_id = ? AND idem_key = ? AND status = 'IN_PROGRESS'", userId, key);
    }
}
