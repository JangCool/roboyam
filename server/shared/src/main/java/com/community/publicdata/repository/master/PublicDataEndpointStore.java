package com.community.publicdata.repository.master;

import com.community.operator.entity.QOperatorEntity;
import com.community.publicdata.entity.QPublicDataEndpointEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 공공데이터 API 주소 표({@code public_data_endpoints}, shared V150 — API 마다 한 줄: {@code NTS}·{@code HOLIDAY}) 읽기·쓰기 — {@code PublicDataEndpointService} 만 부른다.
 * 빈 칸(NULL)은 코드의 공식 기본값. 요청마다 읽는다(작은 표 한 줄 — 서버가 여럿이어도 저장 직후부터 같은 주소를 쓴다). 트랜잭션은 부르는 서비스의 것을 따른다.
 *
 * <p>Native SQL(JDBC)로 남긴 것: 저장·연결 시험 기록의 {@code INSERT … ON CONFLICT (api) DO UPDATE}(처음 저장할 때 줄이 없다 — 두 요청이 동시에 처음 저장해도 한 줄).
 * {@code JdbcTemplate} 이라 부르는 쪽 트랜잭션이 있으면 같은 연결로 참여하고, 없으면(연결 시험 기록은 바깥 HTTP 를 부르므로 트랜잭션 밖) 한 문장으로 바로 커밋한다 — 예전과 같다.
 */
@Repository
public class PublicDataEndpointStore extends RoboyamRepositoryQDSL {

    /**
     * 한 줄.
     *
     * @param updatedByName 마지막으로 저장·되돌린 운영자 이름
     * @param testedByName  마지막으로 연결 시험한 운영자 이름(특일 정보만 — 국세청 시험은 {@code public_data_settings} 에 남는다)
     */
    public record Row(String api, String baseUrl, String queryTemplate, String dateField, String nameField, Instant updatedAt, String updatedByName, String testedResult,
                      Instant testedAt, String testedByName) {
    }

    private static final QPublicDataEndpointEntity E = QPublicDataEndpointEntity.publicDataEndpointEntity;

    private final JdbcTemplate jdbc;

    public PublicDataEndpointStore(JPAQueryFactory query, DataSource dataSource) {
        super(query);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public Optional<Row> find(String api) {
        QOperatorEntity u = new QOperatorEntity("updater");
        QOperatorEntity t = new QOperatorEntity("tester");
        return Optional.ofNullable(query.select(Projections.constructor(Row.class, E.api, E.baseUrl, E.queryTemplate, E.dateField, E.nameField, E.updatedAt,
                        u.operatorName, E.testedResult, E.testedAt, t.operatorName))
                .from(E).leftJoin(u).on(u.id.eq(E.updatedBy)).leftJoin(t).on(t.id.eq(E.testedBy)).where(E.api.eq(api)).fetchOne());
    }

    /** 값을 저장한다(없으면 만든다 — null 칸은 기본값). 옛 주소로 한 연결 시험 결과는 지운다 */
    public void save(String api, String baseUrl, String queryTemplate, String dateField, String nameField, long updatedBy, Instant now) {
        jdbc.update("INSERT INTO public_data_endpoints (api, base_url, query_template, date_field, name_field, updated_at, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (api) DO UPDATE SET base_url = EXCLUDED.base_url, query_template = EXCLUDED.query_template, date_field = EXCLUDED.date_field, "
                        + "name_field = EXCLUDED.name_field, updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by, tested_result = NULL, tested_at = NULL, tested_by = NULL",
                api, baseUrl, queryTemplate, dateField, nameField, Timestamp.from(now), updatedBy);
    }

    /** 마지막 연결 시험을 남긴다(줄이 없으면 만든다 — 값 없이) */
    public void recordTest(String api, String result, long testedBy, Instant now) {
        jdbc.update("INSERT INTO public_data_endpoints (api, tested_result, tested_at, tested_by) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (api) DO UPDATE SET tested_result = EXCLUDED.tested_result, tested_at = EXCLUDED.tested_at, tested_by = EXCLUDED.tested_by",
                api, result, Timestamp.from(now), testedBy);
    }
}
