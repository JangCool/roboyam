package com.community.mfa.repository.master;

import com.community.mfa.dto.MfaOperatorStatus;
import com.community.mfa.dto.MfaSettings;
import com.community.mfa.dto.OperatorMfaRow;
import com.community.mfa.dto.TrustedDeviceRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 관리자 2차 인증 표(admin 스트림 V3 — {@code admin_security_settings} · {@code operator_mfa} · {@code operator_mfa_recovery_codes} · {@code operator_trusted_devices}) 읽기·쓰기.
 *
 * <p>JDBC(native SQL)인 이유: admin 서버만 쓰는 admin 스트림의 표다 — 엔티티·Q 클래스는 shared 모듈에서만 만들어지고, shared 에 엔티티를 두면 이 표가 없는 공개 서버(api)의
 * 엔티티 목록에도 들어간다(관리자 메뉴 {@code AdminMenuQueries} 와 같은 판단). 조건부 갱신({@code last_step < ?}, {@code used_at IS NULL})으로 동시 요청의 재사용을 막는다.
 * 트랜잭션은 부르는 서비스의 것을 따른다(같은 DataSource). 비밀키는 암호문으로만 오가고, 복구 코드·쿠키는 해시만 다룬다.
 */
@Repository
public class MfaStore {

    /** 지금 로그인할 수 있는 운영자 — {@code OperatorEntity#canLogin} · {@code NotificationRecipientRepositoryQDSL#isActive} 와 같은 조건 */
    private static final String ACTIVE_OPERATOR = "o.approved AND NOT o.locked AND NOT o.expired AND o.enabled "
            + "AND (o.account_start_at IS NULL OR o.account_start_at <= ?) AND (o.account_end_at IS NULL OR o.account_end_at >= ?)";

    private final JdbcTemplate jdbc;

    public MfaStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 설정 ─────────────────────────────────────────────────────────────────

    /** 설정 행(마이그레이션이 넣는다 — 없으면 기본값 행을 만든다) */
    public MfaSettings settings() {
        return readSettings("");
    }

    /** 설정 행을 쓰기 잠금으로 읽는다 — 동시 저장을 줄 세운다 */
    public MfaSettings lockSettings() {
        return readSettings(" FOR UPDATE");
    }

    private MfaSettings readSettings(String suffix) {
        List<MfaSettings> rows = jdbc.query("SELECT mfa_enabled, grace_days, trust_months, max_failures, mfa_enabled_at, mfa_enabled_by FROM admin_security_settings WHERE id = 1"
                + suffix, (rs, i) -> new MfaSettings(rs.getBoolean(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), instant(rs, 5), nullableLong(rs, 6)));
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        jdbc.update("INSERT INTO admin_security_settings (id) VALUES (1) ON CONFLICT (id) DO NOTHING");
        return readSettings(suffix);
    }

    public void saveSettings(MfaSettings next, long updatedBy, Instant now) {
        jdbc.update("UPDATE admin_security_settings SET mfa_enabled = ?, grace_days = ?, trust_months = ?, max_failures = ?, mfa_enabled_at = ?, mfa_enabled_by = ?, "
                        + "updated_at = ?, updated_by = ? WHERE id = 1",
                next.enabled(), next.graceDays(), next.trustMonths(), next.maxFailures(), ts(next.enabledAt()), next.enabledBy(), ts(now), updatedBy);
    }

    // ── 운영자 OTP ─────────────────────────────────────────────────────────────

    public Optional<OperatorMfaRow> find(long operatorId) {
        return readMfa(operatorId, "");
    }

    /** 그 운영자의 OTP 행을 쓰기 잠금으로 읽는다(등록 확정·코드 확인을 줄 세운다) */
    public Optional<OperatorMfaRow> lock(long operatorId) {
        return readMfa(operatorId, " FOR UPDATE");
    }

    private Optional<OperatorMfaRow> readMfa(long operatorId, String suffix) {
        List<OperatorMfaRow> rows = jdbc.query("SELECT operator_id, secret_enc, enrolled_at, last_step, last_used_at, pending_secret_enc, pending_started_at, pending_reenroll "
                + "FROM operator_mfa WHERE operator_id = ?" + suffix, (rs, i) -> new OperatorMfaRow(rs.getLong(1), rs.getString(2), instant(rs, 3), nullableLong(rs, 4),
                instant(rs, 5), rs.getString(6), instant(rs, 7), rs.getBoolean(8)), operatorId);
        return rows.stream().findFirst();
    }

    /** 등록 중 비밀키를 둔다(이미 있으면 바꾼다). 확정된 비밀키는 그대로다 */
    public void startPending(long operatorId, String pendingSecretEnc, boolean reenroll, Instant now) {
        jdbc.update("INSERT INTO operator_mfa (operator_id, pending_secret_enc, pending_started_at, pending_reenroll, updated_at) VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (operator_id) DO UPDATE SET pending_secret_enc = EXCLUDED.pending_secret_enc, pending_started_at = EXCLUDED.pending_started_at, "
                        + "pending_reenroll = EXCLUDED.pending_reenroll, updated_at = EXCLUDED.updated_at",
                operatorId, pendingSecretEnc, ts(now), reenroll, ts(now));
    }

    /** 등록 중 비밀키를 확정 비밀키로 옮긴다 — 확인에 쓴 칸을 마지막 칸으로 둔다(같은 코드로 곧바로 로그인하지 못하게) */
    public void confirmPending(long operatorId, long acceptedStep, Instant now) {
        jdbc.update("UPDATE operator_mfa SET secret_enc = pending_secret_enc, enrolled_at = ?, last_step = ?, last_used_at = ?, pending_secret_enc = NULL, "
                + "pending_started_at = NULL, pending_reenroll = false, updated_at = ? WHERE operator_id = ? AND pending_secret_enc IS NOT NULL",
                ts(now), acceptedStep, ts(now), ts(now), operatorId);
    }

    /**
     * 받아들인 칸을 저장한다 — 저장된 칸보다 클 때만(동시에 같은 코드를 낸 두 요청 중 하나만 이긴다).
     *
     * @return 저장했으면 true
     */
    public boolean acceptStep(long operatorId, long step, Instant now) {
        return jdbc.update("UPDATE operator_mfa SET last_step = ?, last_used_at = ?, updated_at = ? WHERE operator_id = ? AND secret_enc IS NOT NULL "
                + "AND (last_step IS NULL OR last_step < ?)", step, ts(now), ts(now), operatorId, step) == 1;
    }

    /** 복구 코드로 들어왔을 때 마지막 사용 시각만 */
    public void touchUsed(long operatorId, Instant now) {
        jdbc.update("UPDATE operator_mfa SET last_used_at = ?, updated_at = ? WHERE operator_id = ?", ts(now), ts(now), operatorId);
    }

    /** 그 운영자의 OTP 행(등록·등록 중 모두)을 지운다. 지웠으면 true */
    public boolean deleteMfa(long operatorId) {
        return jdbc.update("DELETE FROM operator_mfa WHERE operator_id = ?", operatorId) > 0;
    }

    // ── 복구 코드 ─────────────────────────────────────────────────────────────

    /** 그 운영자의 복구 코드를 모두 지우고 새 해시들로 바꾼다 */
    public void replaceRecoveryCodes(long operatorId, List<Long> ids, List<String> hashes, Instant now) {
        jdbc.update("DELETE FROM operator_mfa_recovery_codes WHERE operator_id = ?", operatorId);
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < hashes.size(); i++) {
            rows.add(new Object[] {ids.get(i), operatorId, hashes.get(i), ts(now)});
        }
        jdbc.batchUpdate("INSERT INTO operator_mfa_recovery_codes (id, operator_id, code_hash, created_at) VALUES (?, ?, ?, ?)", rows);
    }

    /**
     * 쓰지 않은 그 복구 코드를 쓴 것으로 표시한다(조건부 — 같은 코드를 두 번 쓰지 못한다).
     *
     * @return 표시했으면 true
     */
    public boolean useRecoveryCode(long operatorId, String codeHash, Instant now) {
        return jdbc.update("UPDATE operator_mfa_recovery_codes SET used_at = ? WHERE operator_id = ? AND code_hash = ? AND used_at IS NULL",
                ts(now), operatorId, codeHash) == 1;
    }

    public int unusedRecoveryCodes(long operatorId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM operator_mfa_recovery_codes WHERE operator_id = ? AND used_at IS NULL", Integer.class, operatorId);
        return count == null ? 0 : count;
    }

    public int deleteRecoveryCodes(long operatorId) {
        return jdbc.update("DELETE FROM operator_mfa_recovery_codes WHERE operator_id = ?", operatorId);
    }

    // ── 신뢰 기기 ─────────────────────────────────────────────────────────────

    public void insertDevice(long id, long operatorId, String tokenHash, String agent, String ip, Instant trustedAt, Instant expiresAt) {
        jdbc.update("INSERT INTO operator_trusted_devices (id, operator_id, token_hash, agent, ip, trusted_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, operatorId, tokenHash, agent, ip, ts(trustedAt), ts(expiresAt));
    }

    /** 그 운영자의, 기간이 남은 기기 중 쿠키 해시가 같은 것 — 다른 운영자의 기기 기록으로는 찾지 않는다 */
    public Optional<Long> findValidDevice(long operatorId, String tokenHash, Instant now) {
        return jdbc.query("SELECT id FROM operator_trusted_devices WHERE operator_id = ? AND token_hash = ? AND expires_at > ?",
                (rs, i) -> rs.getLong(1), operatorId, tokenHash, ts(now)).stream().findFirst();
    }

    public void touchDevice(long id, Instant now) {
        jdbc.update("UPDATE operator_trusted_devices SET last_used_at = ? WHERE id = ?", ts(now), id);
    }

    /** 기간이 남은 기기들(최근 신뢰 차례). {@code currentHash} 와 같은 기기가 {@code current} */
    public List<TrustedDeviceRow> devices(long operatorId, String currentHash, Instant now) {
        return jdbc.query("SELECT id, agent, ip, trusted_at, expires_at, last_used_at, token_hash FROM operator_trusted_devices WHERE operator_id = ? AND expires_at > ? "
                        + "ORDER BY trusted_at DESC, id DESC",
                (rs, i) -> new TrustedDeviceRow(rs.getLong(1), rs.getString(2), rs.getString(3), instant(rs, 4), instant(rs, 5), instant(rs, 6),
                        currentHash != null && currentHash.equals(rs.getString(7).trim())), operatorId, ts(now));
    }

    public int countDevices(long operatorId, Instant now) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM operator_trusted_devices WHERE operator_id = ? AND expires_at > ?", Integer.class, operatorId, ts(now));
        return count == null ? 0 : count;
    }

    /** 그 운영자의 기기 하나의 쿠키 해시(내 것이 아니면 비어 있다) */
    public Optional<String> deviceHash(long operatorId, long id) {
        return jdbc.query("SELECT token_hash FROM operator_trusted_devices WHERE operator_id = ? AND id = ?", (rs, i) -> rs.getString(1).trim(), operatorId, id)
                .stream().findFirst();
    }

    /** 그 운영자의 기기 하나를 지운다. 지웠으면 true */
    public boolean deleteDevice(long operatorId, long id) {
        return jdbc.update("DELETE FROM operator_trusted_devices WHERE operator_id = ? AND id = ?", operatorId, id) == 1;
    }

    /** 그 운영자의 기기를 모두 지운다(기간이 끝난 것 포함). 지운 수 */
    public int deleteDevices(long operatorId) {
        return jdbc.update("DELETE FROM operator_trusted_devices WHERE operator_id = ?", operatorId);
    }

    // ── 등록 현황 ─────────────────────────────────────────────────────────────

    /** 활성 운영자(루트 포함)와 등록 여부 — ID 차례 */
    public List<MfaOperatorStatus> activeOperators(Instant now) {
        return jdbc.query("SELECT o.id, o.operator_name, o.created_at, (m.secret_enc IS NOT NULL) FROM operators o LEFT JOIN operator_mfa m ON m.operator_id = o.id WHERE "
                + ACTIVE_OPERATOR + " ORDER BY o.id", (rs, i) -> new MfaOperatorStatus(rs.getLong(1), rs.getString(2), instant(rs, 3), rs.getBoolean(4)), ts(now), ts(now));
    }

    private static OffsetDateTime ts(Instant at) {
        return at == null ? null : OffsetDateTime.ofInstant(at.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC); // DB 는 마이크로초 — 반올림되지 않게 먼저 자른다
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(ResultSet rs, int column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
