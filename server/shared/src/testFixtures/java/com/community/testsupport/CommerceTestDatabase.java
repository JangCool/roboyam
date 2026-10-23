package com.community.testsupport;

import com.community.zconfiguration.flyway.SchemaMigration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * <b>커머스 테스트 전용</b> 실제 PostgreSQL 시험 DB(Testcontainers). 잠금·동시성·조건부 UPDATE·{@code SKIP LOCKED}·트리거·제약처럼 실제 DB 가 있어야 하는 커머스 규칙을
 * 시험한다(docs/domain/commerce.md 18절 — Testcontainers 는 커머스 테스트에만 쓰기로 승인 2026-10-23, 다른 기능의 시험에 쓰지 않는다).
 *
 * <ul>
 *   <li>시험 JVM 하나에 컨테이너 하나를 처음 부를 때 띄우고 끝날 때 Testcontainers 가 치운다. 이미지는 운영과 같은 메이저인 {@code postgres:18}(pg_cron 없음 —
 *       마이그레이션은 pg_cron 이 없으면 등록을 건너뛰고, DB 배치 프로시저는 {@code CALL} 로 직접 부를 수 있다).</li>
 *   <li>스키마는 앱과 같은 길({@link SchemaMigration#STANDARD} — shared → 자기 스트림)로 만든다. 여러 번 불러도 이관은 한 번이다.</li>
 *   <li>DB 하나를 시험들이 같이 쓴다 — 시험은 자기가 만든 행만 보고(새 ID), 지워야 하면 스스로 지운다. 표 전체를 비우지 않는다(다른 시험 클래스의 행이 있다).</li>
 *   <li>시험 PC 에 Docker 가 있어야 한다.</li>
 * </ul>
 */
public final class CommerceTestDatabase {

    /** 운영 DB 와 같은 메이저 버전 */
    public static final String IMAGE = "postgres:18";

    private static PostgreSQLContainer container;
    private static DataSource dataSource;
    private static final Set<String> MIGRATED_STREAMS = new HashSet<>();

    private CommerceTestDatabase() {
    }

    /**
     * 컨테이너를 (처음이면) 띄우고 shared 와 {@code ownStreams}(예: {@code admin}) 스트림을 이관한 데이터소스를 준다. 연결은 부를 때마다 새로 연다(풀 없음) —
     * 동시성 시험은 스레드마다 {@code getConnection()} 으로 따로 연결을 잡는다.
     */
    public static synchronized DataSource migrated(String... ownStreams) {
        if (container == null) {
            container = new PostgreSQLContainer(IMAGE).withDatabaseName("roboyam").withUsername("roboyam").withPassword("roboyam");
            container.start();
            DriverManagerDataSource source = new DriverManagerDataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword());
            source.setDriverClassName("org.postgresql.Driver");
            dataSource = source;
        }
        List<String> pending = java.util.Arrays.stream(ownStreams).filter(stream -> !MIGRATED_STREAMS.contains(stream)).toList();
        if (MIGRATED_STREAMS.isEmpty() || !pending.isEmpty()) {
            SchemaMigration.STANDARD.migrate(dataSource, pending);
            MIGRATED_STREAMS.add(SchemaMigration.SHARED);
            MIGRATED_STREAMS.addAll(pending);
        }
        return dataSource;
    }

    /** 스프링 시험 문맥에 넣을 접속 정보(`@DynamicPropertySource` 등) — {@link #migrated} 를 먼저 부른다 */
    public static String jdbcUrl() {
        return requireStarted().getJdbcUrl();
    }

    public static String username() {
        return requireStarted().getUsername();
    }

    public static String password() {
        return requireStarted().getPassword();
    }

    private static synchronized PostgreSQLContainer requireStarted() {
        if (container == null) {
            throw new IllegalStateException("CommerceTestDatabase.migrated(...) 를 먼저 부른다");
        }
        return container;
    }
}
