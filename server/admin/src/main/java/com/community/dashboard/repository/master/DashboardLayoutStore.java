package com.community.dashboard.repository.master;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 운영자 대시보드 구성 표(admin 스트림 V5 — {@code operator_dashboards}) 읽기·쓰기.
 *
 * <p>JDBC(native SQL)인 이유: admin 서버만 쓰는 admin 스트림의 표다 — 엔티티를 shared 에 두면 이 표가 없는 공개 서버(api)의 엔티티 목록에도 들어간다
 * ({@code MfaStore} 와 같은 판단). jsonb 칸은 문자열로 오가고({@code ?::jsonb}), 저장은 {@code ON CONFLICT} 한 문장이라 같은 운영자의 동시 저장도 행 하나로 끝난다(나중 것이 이긴다).
 * 트랜잭션은 부르는 서비스의 것을 따른다.
 */
@Repository
public class DashboardLayoutStore {

    /** 저장된 한 행 — {@code gadgets} 는 JSON 배열 문자열 */
    public record Row(int version, int columns, String gadgets) {
    }

    private final JdbcTemplate jdbc;

    public DashboardLayoutStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public Optional<Row> find(long operatorId) {
        List<Row> rows = jdbc.query("SELECT version, columns, gadgets::text FROM operator_dashboards WHERE operator_id = ?",
                (rs, i) -> new Row(rs.getInt(1), rs.getInt(2), rs.getString(3)), operatorId);
        return rows.stream().findFirst();
    }

    public void save(long operatorId, int version, int columns, String gadgetsJson, Instant now) {
        jdbc.update("INSERT INTO operator_dashboards (operator_id, version, columns, gadgets, updated_at) VALUES (?, ?, ?, ?::jsonb, ?) "
                        + "ON CONFLICT (operator_id) DO UPDATE SET version = EXCLUDED.version, columns = EXCLUDED.columns, gadgets = EXCLUDED.gadgets, updated_at = EXCLUDED.updated_at",
                operatorId, version, columns, gadgetsJson, OffsetDateTime.ofInstant(now.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC));
    }
}
