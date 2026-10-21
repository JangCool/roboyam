package com.community.installation.repository;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.stereotype.Repository;

/**
 * 설치 점검이 "이 서버가 돌아갈 수 있는 환경인가"를 보는 읽기 전용 조회 — 데이터베이스 종류·버전, 적용된 데이터 구조(마이그레이션) 버전,
 * 검색 확장({@code pg_trgm}), DB 로캘의 한글 색인 지원. 접속 문자열·사용자 이름·경로 같은 내부 정보는 돌려주지 않는다.
 *
 * <p>버전은 JDBC 메타데이터로, 마이그레이션은 Flyway 정보로 읽는다. 검색 확장·로캘은 확장의 함수를 <b>네이티브 SQL 로 불러 써 보는 것</b>으로 확인한다 —
 * 엔티티·테이블에 묶이지 않는 DB 전용 함수 호출이라 JPA 로 표현할 수 없다(확장이 없으면 DB 가 오류를 낸다).
 *
 * <p>{@code Flyway} 빈은 <b>shared 스트림</b>({@code db/migration/shared}, 이력 표 {@code flyway_schema_history} — {@code SchemaMigrationConfig})이다.
 * 그래서 "스키마 버전"·"적용 대기"는 api 와 admin 이 같이 쓰는 공통 스키마의 것이다. 이 앱의 자기 스트림({@code api})은 기동 때 이미 이관되었고
 * 대기가 남아 있으면 앱이 뜨지 않으므로 여기서 따로 보지 않는다.
 */
@Repository
public class InstallationEnvironmentRepository {

    private final DataSource dataSource;
    private final Flyway flyway;

    public InstallationEnvironmentRepository(DataSource dataSource, Flyway flyway) {
        this.dataSource = dataSource;
        this.flyway = flyway;
    }

    /** 데이터베이스 이름과 버전(예: {@code PostgreSQL 18.1}). 연결하지 못하면 {@link SQLException} 을 던진다 */
    public String databaseVersion() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            return meta.getDatabaseProductName() + " " + meta.getDatabaseMajorVersion() + "." + meta.getDatabaseMinorVersion();
        }
    }

    /** 적용된 데이터 구조(마이그레이션)의 가장 높은 버전(예: {@code 6}). 하나도 적용되지 않았으면 비어 있다 */
    public Optional<String> currentMigrationVersion() {
        MigrationInfo current = flyway.info().current();
        return current == null || current.getVersion() == null ? Optional.empty() : Optional.of(current.getVersion().getVersion());
    }

    /** 아직 적용되지 않은 마이그레이션이 있는가 */
    public boolean hasPendingMigrations() {
        return flyway.info().pending().length > 0;
    }

    /** {@code pg_trgm} 확장의 함수가 실제로 도는가 — {@code similarity('a', 'a')} 를 불러 본다(확장이 없으면 DB 가 오류를 낸다) */
    public boolean trigramWorks() throws SQLException {
        // 네이티브 SQL 사유: 확장(pg_trgm)의 함수 호출은 JPA 로 표현할 수 없다
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("select similarity('a', 'a')");
             ResultSet result = statement.executeQuery()) {
            return result.next() && result.getDouble(1) > 0;
        }
    }

    /**
     * 이 DB 의 로캘이 <b>한글 글자 조각(trigram)을 만드는가</b> — {@code cardinality(show_trgm('한글')) > 0}. DB 를 {@code --locale=C} 로 만들면
     * pg_trgm 이 한글을 구분자로만 보아 조각이 하나도 나오지 않고, 한글 검색이 색인을 못 타 글 수에 비례하는 전체 읽기가 된다. 로캘은 DB 를 만들 때만 정해져
     * 나중에 못 고치므로 설치 점검이 미리 알린다.
     */
    public boolean koreanTrigramWorks() throws SQLException {
        // 네이티브 SQL 사유: 확장(pg_trgm)의 함수 호출은 JPA 로 표현할 수 없다
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("select cardinality(show_trgm(?))")) {
            statement.setString(1, "한글");
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }
}
