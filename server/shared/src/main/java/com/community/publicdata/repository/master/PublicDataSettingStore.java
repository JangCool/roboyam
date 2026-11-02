package com.community.publicdata.repository.master;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 공공데이터포털 설정 표({@code public_data_settings}, shared V125) 읽기·쓰기 — {@code PublicDataSettingsService} 만 부른다.
 *
 * <p>JDBC(native SQL)인 이유: 칸마다 한 줄인 키-값 표({@code portone_settings} 와 같은 모양)라 {@code INSERT … ON CONFLICT DO UPDATE} 한 문장으로 저장하고,
 * 바꾼 사람·시험한 사람 이름을 운영자 표와 이어 한 번에 읽는다. 값은 암호문({@code v1:})으로만 오간다. 트랜잭션은 부르는 서비스의 것을 따른다.
 */
@Repository
public class PublicDataSettingStore {

    /**
     * 칸 한 줄. {@code valueCipher} 가 null 이면 지웠거나 저장한 적 없는 칸.
     *
     * @param updatedByName 마지막으로 저장·지운 운영자 이름(없거나 지워졌으면 null)
     * @param testedByName  마지막으로 연결 시험한 운영자 이름
     */
    public record Row(String key, String valueCipher, Instant updatedAt, String updatedByName, String testedResult, Instant testedAt, String testedByName) {

        public boolean configured() {
            return valueCipher != null;
        }

        @Override
        public String toString() {
            return "Row[key=" + key + ", configured=" + configured() + ", updatedAt=" + updatedAt + "]"; // 암호문도 싣지 않는다
        }
    }

    private final JdbcTemplate jdbc;

    public PublicDataSettingStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public Optional<Row> find(String key) {
        return jdbc.query("SELECT s.key, s.value_cipher, s.updated_at, u.operator_name, s.tested_result, s.tested_at, t.operator_name FROM public_data_settings s "
                        + "LEFT JOIN operators u ON u.id = s.updated_by LEFT JOIN operators t ON t.id = s.tested_by WHERE s.key = ?",
                (rs, i) -> new Row(rs.getString(1), rs.getString(2), instant(rs.getTimestamp(3)), rs.getString(4), rs.getString(5), instant(rs.getTimestamp(6)),
                        rs.getString(7)), key).stream().findFirst();
    }

    /** 암호문만(조회할 때마다 읽는다 — 이름을 잇지 않는다) */
    public Optional<String> cipher(String key) {
        return jdbc.query("SELECT value_cipher FROM public_data_settings WHERE key = ? AND value_cipher IS NOT NULL", (rs, i) -> rs.getString(1), key).stream().findFirst();
    }

    /** 값을 저장한다(없으면 만든다) — 옛 키로 한 연결 시험 결과는 지운다 */
    public void upsert(String key, String valueCipher, long updatedBy, Instant now) {
        jdbc.update("INSERT INTO public_data_settings (key, value_cipher, updated_at, updated_by) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (key) DO UPDATE SET value_cipher = EXCLUDED.value_cipher, updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by, "
                        + "tested_result = NULL, tested_at = NULL, tested_by = NULL",
                key, valueCipher, ts(now), updatedBy);
    }

    /**
     * 설정된 값을 지운다(줄은 남기고 값만 NULL — 「마지막 변경」이 남는다). 연결 시험 결과도 지운다.
     *
     * @return 지웠으면 true(설정돼 있지 않았으면 false)
     */
    public boolean clear(String key, long updatedBy, Instant now) {
        return jdbc.update("UPDATE public_data_settings SET value_cipher = NULL, updated_at = ?, updated_by = ?, tested_result = NULL, tested_at = NULL, tested_by = NULL "
                + "WHERE key = ? AND value_cipher IS NOT NULL", ts(now), updatedBy, key) == 1;
    }

    /** 마지막 연결 시험을 남긴다(줄이 없으면 만든다 — 값 없이) */
    public void recordTest(String key, String result, long testedBy, Instant now) {
        jdbc.update("INSERT INTO public_data_settings (key, tested_result, tested_at, tested_by) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (key) DO UPDATE SET tested_result = EXCLUDED.tested_result, tested_at = EXCLUDED.tested_at, tested_by = EXCLUDED.tested_by",
                key, result, ts(now), testedBy);
    }

    private static Instant instant(Timestamp at) {
        return at == null ? null : at.toInstant();
    }

    private static OffsetDateTime ts(Instant at) {
        return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
