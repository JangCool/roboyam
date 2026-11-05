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
 * 공공데이터 API 주소 표({@code public_data_endpoints}, shared V150 — API 마다 한 줄: {@code NTS}·{@code HOLIDAY}) 읽기·쓰기 — {@code PublicDataEndpointService} 만 부른다.
 * 빈 칸(NULL)은 코드의 공식 기본값. 요청마다 읽는다(작은 표 한 줄 — 서버가 여럿이어도 저장 직후부터 같은 주소를 쓴다). 트랜잭션은 부르는 서비스의 것을 따른다.
 */
@Repository
public class PublicDataEndpointStore {

    /**
     * 한 줄.
     *
     * @param updatedByName 마지막으로 저장·되돌린 운영자 이름
     * @param testedByName  마지막으로 연결 시험한 운영자 이름(특일 정보만 — 국세청 시험은 {@code public_data_settings} 에 남는다)
     */
    public record Row(String api, String baseUrl, String queryTemplate, String dateField, String nameField, Instant updatedAt, String updatedByName, String testedResult,
                      Instant testedAt, String testedByName) {
    }

    private final JdbcTemplate jdbc;

    public PublicDataEndpointStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public Optional<Row> find(String api) {
        return jdbc.query("SELECT e.api, e.base_url, e.query_template, e.date_field, e.name_field, e.updated_at, u.operator_name, e.tested_result, e.tested_at, t.operator_name "
                        + "FROM public_data_endpoints e LEFT JOIN operators u ON u.id = e.updated_by LEFT JOIN operators t ON t.id = e.tested_by WHERE e.api = ?",
                (rs, i) -> new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), instant(rs.getTimestamp(6)), rs.getString(7),
                        rs.getString(8), instant(rs.getTimestamp(9)), rs.getString(10)), api).stream().findFirst();
    }

    /** 값을 저장한다(없으면 만든다 — null 칸은 기본값). 옛 주소로 한 연결 시험 결과는 지운다 */
    public void save(String api, String baseUrl, String queryTemplate, String dateField, String nameField, long updatedBy, Instant now) {
        jdbc.update("INSERT INTO public_data_endpoints (api, base_url, query_template, date_field, name_field, updated_at, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (api) DO UPDATE SET base_url = EXCLUDED.base_url, query_template = EXCLUDED.query_template, date_field = EXCLUDED.date_field, "
                        + "name_field = EXCLUDED.name_field, updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by, tested_result = NULL, tested_at = NULL, tested_by = NULL",
                api, baseUrl, queryTemplate, dateField, nameField, ts(now), updatedBy);
    }

    /** 마지막 연결 시험을 남긴다(줄이 없으면 만든다 — 값 없이) */
    public void recordTest(String api, String result, long testedBy, Instant now) {
        jdbc.update("INSERT INTO public_data_endpoints (api, tested_result, tested_at, tested_by) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (api) DO UPDATE SET tested_result = EXCLUDED.tested_result, tested_at = EXCLUDED.tested_at, tested_by = EXCLUDED.tested_by",
                api, result, ts(now), testedBy);
    }

    private static Instant instant(Timestamp at) {
        return at == null ? null : at.toInstant();
    }

    private static OffsetDateTime ts(Instant at) {
        return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
