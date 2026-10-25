package com.community.portone.repository.master;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 포트원 설정 표({@code portone_settings}, shared V84) 읽기·쓰기 — {@code PortOneSettingsService} 만 부른다.
 *
 * <p>JDBC(native SQL)인 이유: 칸마다 한 줄인 키-값 표라 {@code INSERT … ON CONFLICT DO UPDATE} 한 문장으로 저장하고, 바꾼 사람 이름을 운영자 표와 이어 한 번에 읽는다.
 * 값은 암호문({@code v1:})으로만 오간다. 트랜잭션은 부르는 서비스의 것을 따른다.
 */
@Repository
public class PortOneSettingStore {

    /**
     * 칸 한 줄. {@code valueCipher} 가 null 이면 지운 칸(바꾼 시각·사람은 남는다).
     *
     * @param updatedByName 바꾼 운영자 이름(운영자가 지워졌거나 없으면 null)
     */
    public record Row(String key, String valueCipher, Instant updatedAt, Long updatedBy, String updatedByName) {

        public boolean configured() {
            return valueCipher != null;
        }

        @Override
        public String toString() {
            return "Row[key=" + key + ", configured=" + configured() + ", updatedAt=" + updatedAt + "]"; // 암호문도 싣지 않는다
        }
    }

    private final JdbcTemplate jdbc;

    public PortOneSettingStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 모든 칸(지운 칸 포함) */
    public List<Row> findAll() {
        return jdbc.query("SELECT s.key, s.value_cipher, s.updated_at, s.updated_by, o.operator_name FROM portone_settings s "
                + "LEFT JOIN operators o ON o.id = s.updated_by ORDER BY s.key", (rs, i) -> new Row(rs.getString(1), rs.getString(2),
                rs.getTimestamp(3).toInstant(), rs.getObject(4) == null ? null : rs.getLong(4), rs.getString(5)));
    }

    /** 칸 값을 저장한다(없으면 만든다) */
    public void upsert(String key, String valueCipher, long updatedBy, Instant now) {
        jdbc.update("INSERT INTO portone_settings (key, value_cipher, updated_at, updated_by) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (key) DO UPDATE SET value_cipher = EXCLUDED.value_cipher, updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by",
                key, valueCipher, ts(now), updatedBy);
    }

    /**
     * 설정된 칸의 값을 지운다(줄은 남기고 값만 NULL — 「마지막 변경」이 남는다).
     *
     * @return 지웠으면 true(설정돼 있지 않았으면 false)
     */
    public boolean clear(String key, long updatedBy, Instant now) {
        return jdbc.update("UPDATE portone_settings SET value_cipher = NULL, updated_at = ?, updated_by = ? WHERE key = ? AND value_cipher IS NOT NULL",
                ts(now), updatedBy, key) == 1;
    }

    private static OffsetDateTime ts(Instant at) {
        return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
