package com.community.withdrawal.repository.master;

import com.community.withdrawal.entity.WithdrawalKind;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 재가입·재인증 제한 해시 표({@code identity_withdrawals}, shared V84) — {@code IdentityWithdrawalRecorder} 와 본인인증 확정이 부른다. 해시만 다룬다(누구의 것인지 잇는 칸이 없다).
 * JDBC 인 이유: 같은 종류·해시의 「가장 늦은 끝」을 한 문장으로 읽고, 본인인증 확정과 같은 트랜잭션(JDBC)에서 쓴다.
 */
@Repository
public class IdentityWithdrawalStore {

    private final JdbcTemplate jdbc;

    public IdentityWithdrawalStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void insert(long id, WithdrawalKind kind, String hash, Instant withdrawnAt, Instant expiresAt) {
        jdbc.update("INSERT INTO identity_withdrawals (id, kind, hash, withdrawn_at, expires_at) VALUES (?, ?, ?, ?, ?)", id, kind.name(), hash, ts(withdrawnAt),
                ts(expiresAt));
    }

    /** 그 종류·해시가 {@code now} 뒤까지 막혀 있으면 가장 늦은 끝 */
    public Optional<Instant> blockedUntil(WithdrawalKind kind, String hash, Instant now) {
        if (hash == null) {
            return Optional.empty();
        }
        OffsetDateTime until = jdbc.queryForObject("SELECT max(expires_at) FROM identity_withdrawals WHERE kind = ? AND hash = ? AND expires_at > ?",
                OffsetDateTime.class, kind.name(), hash, ts(now));
        return Optional.ofNullable(until).map(OffsetDateTime::toInstant);
    }

    private static OffsetDateTime ts(Instant at) {
        return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
