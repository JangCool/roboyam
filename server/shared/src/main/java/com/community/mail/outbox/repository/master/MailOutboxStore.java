package com.community.mail.outbox.repository.master;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 커머스 메일 대기열({@code mail_outbox}) 읽기·쓰기. 상태 전이는 모두 한 문장 조건부 UPDATE({@code … WHERE id = ? AND status = 'SENDING' AND locked_by = ?}) — 이긴 쪽만 바꾼다.
 *
 * <p>Native SQL(JDBC)인 이유: {@code INSERT … ON CONFLICT DO NOTHING}(중복 넣기), {@code FOR UPDATE SKIP LOCKED}(서버 여럿이 같은 줄을 두 번 집지 않게),
 * {@code jsonb} 변수 칸을 쓴다. 넣기는 부르는 쪽의 업무 트랜잭션에 실린다.
 */
@Repository
public class MailOutboxStore {

    private final JdbcTemplate jdbc;

    public MailOutboxStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 넣는다 — 같은 열쇠가 이미 있으면 넣지 않는다. 넣었으면 true */
    public boolean insert(long id, String dedupeKey, long userId, String kind, String variablesJson, Instant now) {
        return jdbc.update("INSERT INTO mail_outbox (id, dedupe_key, user_id, mail_kind, variables, status, attempts, next_attempt_at, created_at) "
                + "VALUES (?, ?, ?, ?, CAST(? AS jsonb), 'PENDING', 0, ?, ?) ON CONFLICT (dedupe_key) DO NOTHING",
                id, dedupeKey, userId, kind, variablesJson, ts(now), ts(now)) == 1;
    }

    /**
     * SENDING 으로 {@code staleBefore} 전부터 머문 줄(보내던 워커가 죽었다)을 되돌린다 — 시도가 한도에 닿았으면 DEAD, 아니면 바로 PENDING.
     *
     * @return 되돌린 줄 수
     */
    public int recoverStale(Instant staleBefore, int maxAttempts, Instant now) {
        return jdbc.update("UPDATE mail_outbox SET "
                + "status = CASE WHEN attempts >= ? THEN 'DEAD' ELSE 'PENDING' END, "
                + "variables = CASE WHEN attempts >= ? THEN NULL ELSE variables END, "
                + "finished_at = CASE WHEN attempts >= ? THEN CAST(? AS timestamptz) ELSE NULL END, "
                + "next_attempt_at = ?, locked_at = NULL, locked_by = NULL, last_error = 'STALE_LOCK' "
                + "WHERE status = 'SENDING' AND locked_at < ?", maxAttempts, maxAttempts, maxAttempts, ts(now), ts(now), ts(staleBefore));
    }

    /** 보낼 차례인 줄을 집는다 — {@code FOR UPDATE SKIP LOCKED} 로 다른 워커가 집는 중인 줄은 건너뛰고, SENDING·시도 +1 로 바꿔 커밋한다 */
    public List<Claimed> claim(int limit, int nodeId, Instant now) {
        return jdbc.query("UPDATE mail_outbox SET status = 'SENDING', locked_at = ?, locked_by = ?, attempts = attempts + 1 "
                + "WHERE id IN (SELECT id FROM mail_outbox WHERE status = 'PENDING' AND next_attempt_at <= ? ORDER BY next_attempt_at, id LIMIT ? FOR UPDATE SKIP LOCKED) "
                + "RETURNING id, user_id, mail_kind, variables::text, attempts",
                (rs, i) -> new Claimed(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getInt(5)), ts(now), nodeId, ts(now), limit);
    }

    /** 보냈다 — 본문(변수)을 지우고 가린 주소만 남긴다 */
    public boolean markSent(long id, int nodeId, String toMasked, Instant now) {
        return jdbc.update("UPDATE mail_outbox SET status = 'SENT', sent_at = ?, finished_at = ?, to_masked = ?, variables = NULL, last_error = NULL, "
                + "locked_at = NULL, locked_by = NULL WHERE id = ? AND status = 'SENDING' AND locked_by = ?", ts(now), ts(now), toMasked, id, nodeId) == 1;
    }

    /** 다시 시도한다 — {@code nextAttemptAt} 부터 다시 집힌다 */
    public boolean markRetry(long id, int nodeId, String error, Instant nextAttemptAt) {
        return jdbc.update("UPDATE mail_outbox SET status = 'PENDING', next_attempt_at = ?, last_error = ?, locked_at = NULL, locked_by = NULL "
                + "WHERE id = ? AND status = 'SENDING' AND locked_by = ?", ts(nextAttemptAt), error, id, nodeId) == 1;
    }

    /** 끝낸다(DEAD 재시도 한도 · CANCELED 받을 수 없음) — 본문(변수)을 지운다 */
    public boolean markFinished(long id, int nodeId, String status, String error, Instant now) {
        return jdbc.update("UPDATE mail_outbox SET status = ?, finished_at = ?, last_error = ?, variables = NULL, locked_at = NULL, locked_by = NULL "
                + "WHERE id = ? AND status = 'SENDING' AND locked_by = ?", status, ts(now), error, id, nodeId) == 1;
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    /** 집은 줄 — {@code attempts} 는 이번 시도를 센 뒤의 값 */
    public record Claimed(long id, long userId, String kind, String variablesJson, int attempts) {

        @Override
        public String toString() {
            return "Claimed[id=" + id + ", kind=" + kind + ", attempts=" + attempts + "]"; // 변수(개인정보일 수 있다)는 감춘다
        }
    }
}
