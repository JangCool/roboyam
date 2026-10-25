package com.community.withdrawal.repository.master;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 탈퇴 회원 연락용 이메일 표({@code withdrawn_member_contacts}, shared V62) — {@code WithdrawnContactStore} 만 부른다(일반 회원 조회는 이 표를 읽지 않는다 — 논리적 분리, commerce.md 17-2).
 * 이메일은 암호문({@code d<버전>:})으로만 오간다. JDBC 인 이유: 데이터 키 교체 배치(JDBC)와 같은 칸을 같은 방식으로 다루고, 회원 엔티티 목록에 이 표가 끼지 않게.
 */
@Repository
public class WithdrawnContactRows {

    /** 한 줄 — {@code toString} 은 암호문을 싣지 않는다 */
    public record Row(long userId, String emailCipher, Instant withdrawnAt, Instant purgeAfter) {

        @Override
        public String toString() {
            return "Row[userId=" + userId + ", withdrawnAt=" + withdrawnAt + ", purgeAfter=" + purgeAfter + "]";
        }
    }

    /** 감사 기록에서 읽은 열람 한 건 */
    public record Reveal(String operatorName, Instant revealedAt, String reason) {
    }

    private final JdbcTemplate jdbc;

    public WithdrawnContactRows(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 넣는다(이미 있으면 바꾼다 — 탈퇴는 한 번이지만 다시 부르면 마지막 값) */
    public void upsert(long userId, String emailCipher, Instant withdrawnAt, Instant purgeAfter) {
        jdbc.update("INSERT INTO withdrawn_member_contacts (user_id, email_cipher, withdrawn_at, purge_after) VALUES (?, ?, ?, ?) ON CONFLICT (user_id) DO UPDATE SET "
                + "email_cipher = EXCLUDED.email_cipher, withdrawn_at = EXCLUDED.withdrawn_at, purge_after = EXCLUDED.purge_after", userId, emailCipher, ts(withdrawnAt),
                ts(purgeAfter));
    }

    public Optional<Row> find(long userId) {
        return jdbc.query("SELECT user_id, email_cipher, withdrawn_at, purge_after FROM withdrawn_member_contacts WHERE user_id = ?", WithdrawnContactRows::row, userId)
                .stream().findFirst();
    }

    /**
     * 그 회원 연락처의 열람 기록 — 감사 기록({@code WITHDRAWN_CONTACT_REVEAL}, 대상 MEMBER)에서 읽는다(최근 것부터). 운영자가 지워졌으면 이름은 null.
     */
    public List<Reveal> reveals(long userId, String action, int limit) {
        return jdbc.query("SELECT o.operator_name, a.occurred_at, a.detail::jsonb ->> 'reason' FROM audit_logs a LEFT JOIN operators o ON o.id = a.actor_id "
                        + "WHERE a.action = ? AND a.target_type = 'MEMBER' AND a.target_id = ? ORDER BY a.id DESC LIMIT ?",
                (rs, i) -> new Reveal(rs.getString(1), rs.getObject(2, OffsetDateTime.class).toInstant(), rs.getString(3)), action, Long.toString(userId), limit);
    }

    private static Row row(ResultSet rs, int i) throws SQLException {
        return new Row(rs.getLong(1), rs.getString(2), rs.getObject(3, OffsetDateTime.class).toInstant(), rs.getObject(4, OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime ts(Instant at) {
        return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
