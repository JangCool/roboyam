package com.community.audit.repository.master;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditRetentionClass;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 감사 보관 기간 — DB 배치 {@code audit-log-retention} 의 설정값({@code batch_params}, 칸 {@link AuditRetentionClass#paramKey()})을 읽고 쓴다.
 * 마지막 저장은 감사 {@code AUDIT_RETENTION_UPDATE} 의 가장 최근 줄로 본다(설정값 표에는 바꾼 사람이 없다).
 *
 * <p>Native SQL(JDBC)인 이유: {@code batch_params} 는 DB 배치 프로시저가 {@code batch_param(…)} 으로 읽는 표라 엔티티가 없고, 저장은 {@code INSERT … ON CONFLICT} 다.
 * 부르는 서비스의 트랜잭션에 실린다.
 */
@Repository
public class AuditRetentionStore {

    private final JdbcTemplate jdbc;

    public AuditRetentionStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 분류별 저장된 일수(없는 분류는 빠진다) — {@code forUpdate} 면 줄을 잠근다 */
    public Map<AuditRetentionClass, Long> stored(boolean forUpdate) {
        Map<AuditRetentionClass, Long> out = new EnumMap<>(AuditRetentionClass.class);
        jdbc.query("SELECT param_key, param_value FROM batch_params WHERE job_key = ?" + (forUpdate ? " FOR UPDATE" : ""), rs -> {
            String key = rs.getString(1);
            for (AuditRetentionClass category : AuditRetentionClass.values()) {
                if (category.paramKey().equals(key)) {
                    out.put(category, rs.getLong(2));
                }
            }
        }, AuditRetentionClass.JOB_KEY);
        return out;
    }

    public void save(AuditRetentionClass category, long days, Instant now) {
        jdbc.update("INSERT INTO batch_params (job_key, param_key, param_value, updated_at) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (job_key, param_key) DO UPDATE SET param_value = EXCLUDED.param_value, updated_at = EXCLUDED.updated_at",
                AuditRetentionClass.JOB_KEY, category.paramKey(), days, Timestamp.from(now));
    }

    /** 마지막 저장(성공한 {@code AUDIT_RETENTION_UPDATE}) — 시각과 운영자 ID */
    public Optional<LastChange> lastChange() {
        List<LastChange> rows = jdbc.query("SELECT occurred_at, actor_id FROM audit_logs WHERE action = ? AND result = 'SUCCESS' ORDER BY occurred_at DESC, id DESC LIMIT 1",
                (rs, i) -> new LastChange(rs.getTimestamp(1).toInstant(), (Long) rs.getObject(2)), AuditActions.AUDIT_RETENTION_UPDATE);
        return rows.stream().findFirst();
    }

    public record LastChange(Instant at, Long operatorId) {
    }
}
