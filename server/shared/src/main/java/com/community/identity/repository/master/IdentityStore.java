package com.community.identity.repository.master;

import com.community.identity.dto.IdentityProviderKind;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 본인인증 표(shared V84 — {@code identity_verifications} · {@code identity_verification_attempts}) 읽기·쓰기 — {@code IdentityVerificationService}·{@code IdentityWithdrawalRecorder} 가 부른다.
 *
 * <p>JDBC(native SQL)인 이유: 1인 1계정은 DI·CI·업체 건 번호의 <b>유일 제약</b>이 마지막으로 지키는데, 동시에 들어온 두 확정 중 진 쪽이 예외로 트랜잭션을 망가뜨리지 않게
 * {@code INSERT … ON CONFLICT DO NOTHING}(들어갔는지 행 수로 안다)을 쓴다. 시도 상태는 조건부 UPDATE(발급 상태일 때만)로 한 번만 바꾼다. 트랜잭션은 부르는 서비스가 연다.
 * 해시만 다룬다 — CI·DI 원문은 이 클래스에 오지 않는다.
 */
@Repository
public class IdentityStore {

    /** 확정된 본인인증 한 줄(해시 포함 — 화면에 내지 않는다) */
    public record Verification(long userId, String diHash, String ciHash, LocalDate birthDate, IdentityProviderKind provider, Instant verifiedAt) {
    }

    /** 인증 시도 한 줄 */
    public record Attempt(String id, long userId, IdentityProviderKind provider, String status, String failureCode, Instant createdAt, Instant completedAt) {

        public boolean pending() {
            return "PENDING".equals(status);
        }
    }

    private static final String VERIFICATION_COLUMNS = "user_id, di_hash, ci_hash, birth_date, provider, verified_at";
    private static final String ATTEMPT_COLUMNS = "id, user_id, provider, status, failure_code, created_at, completed_at";

    private final JdbcTemplate jdbc;

    public IdentityStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 본인인증 결과 ──────────────────────────────────────────────────────────

    public Optional<Verification> findVerification(long userId) {
        return jdbc.query("SELECT " + VERIFICATION_COLUMNS + " FROM identity_verifications WHERE user_id = ?", IdentityStore::verification, userId).stream().findFirst();
    }

    /**
     * 본인인증 결과를 넣는다. 그 회원·DI·CI·업체 건 번호 중 하나라도 이미 있으면 넣지 않는다(동시에 들어온 다른 확정이 먼저 넣었으면 그 커밋을 기다린 뒤 넣지 않는다).
     *
     * @return 넣었으면 true
     */
    public boolean insertVerification(long userId, String diHash, String ciHash, LocalDate birthDate, IdentityProviderKind provider, String providerTxId,
                                      Instant verifiedAt) {
        return jdbc.update("INSERT INTO identity_verifications (user_id, di_hash, ci_hash, birth_date, provider, provider_tx_id, verified_at) VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT DO NOTHING", userId, diHash, ciHash, birthDate, provider.name(), providerTxId, ts(verifiedAt)) == 1;
    }

    /** 같은 DI 또는 CI 가 <b>다른</b> 회원에게 묶여 있는가 */
    public boolean linkedToOther(long userId, String diHash, String ciHash) {
        return !jdbc.queryForList("SELECT 1 FROM identity_verifications WHERE user_id <> ? AND ((di_hash IS NOT NULL AND di_hash = ?) OR (ci_hash IS NOT NULL AND ci_hash = ?)) "
                + "LIMIT 1", Integer.class, userId, diHash, ciHash).isEmpty();
    }

    /** 그 회원의 본인인증 결과를 지우고 지운 줄을 돌려준다(탈퇴 — 해시는 재인증 제한 표로 옮긴다) */
    public Optional<Verification> deleteVerification(long userId) {
        return jdbc.query("DELETE FROM identity_verifications WHERE user_id = ? RETURNING " + VERIFICATION_COLUMNS, IdentityStore::verification, userId).stream()
                .findFirst();
    }

    // ── 인증 시도 ─────────────────────────────────────────────────────────────

    public void insertAttempt(String id, long userId, IdentityProviderKind provider, Instant createdAt) {
        jdbc.update("INSERT INTO identity_verification_attempts (id, user_id, provider, status, created_at) VALUES (?, ?, ?, 'PENDING', ?)", id, userId, provider.name(),
                ts(createdAt));
    }

    public Optional<Attempt> findAttempt(String id) {
        return jdbc.query("SELECT " + ATTEMPT_COLUMNS + " FROM identity_verification_attempts WHERE id = ?", IdentityStore::attempt, id).stream().findFirst();
    }

    /** 그 회원의 시도들 — 최근 것부터 */
    public List<Attempt> attemptsOf(long userId) {
        return jdbc.query("SELECT " + ATTEMPT_COLUMNS + " FROM identity_verification_attempts WHERE user_id = ? ORDER BY created_at DESC, id DESC", IdentityStore::attempt,
                userId);
    }

    /**
     * 발급 상태인 시도를 끝낸다(한 번만 — 이미 끝났으면 바꾸지 않는다).
     *
     * @param failureCode 실패면 거절한 오류 코드, 성공이면 null
     * @return 바꿨으면 true
     */
    public boolean finishAttempt(String id, boolean verified, String failureCode, Instant now) {
        return jdbc.update("UPDATE identity_verification_attempts SET status = ?, failure_code = ?, completed_at = ? WHERE id = ? AND status = 'PENDING'",
                verified ? "VERIFIED" : "FAILED", verified ? null : failureCode, ts(now), id) == 1;
    }

    private static Verification verification(ResultSet rs, int row) throws SQLException {
        return new Verification(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getObject(4, LocalDate.class), IdentityProviderKind.valueOf(rs.getString(5)),
                instant(rs, 6));
    }

    private static Attempt attempt(ResultSet rs, int row) throws SQLException {
        return new Attempt(rs.getString(1), rs.getLong(2), IdentityProviderKind.valueOf(rs.getString(3)), rs.getString(4), rs.getString(5), instant(rs, 6),
                instant(rs, 7));
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime ts(Instant at) {
        return at == null ? null : OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
