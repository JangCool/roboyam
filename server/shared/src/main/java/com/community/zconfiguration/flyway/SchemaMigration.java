package com.community.zconfiguration.flyway;

import java.util.Collection;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 스키마 마이그레이션을 <b>스트림</b> 단위로 이관한다.
 *
 * <p>스트림은 "위치 + 이력 표" 한 쌍이다. 앱마다 자기 스트림이 따로 있고 모두 <b>shared 스트림을 먼저</b> 이관한다.
 * <table>
 *   <caption>스트림</caption>
 *   <tr><th>스트림</th><th>위치(클래스패스)</th><th>이력 표</th><th>무엇을 두나</th></tr>
 *   <tr><td>{@code shared}</td><td>{@code db/migration/shared}</td><td>{@code flyway_schema_history}</td><td>api·admin 이 같이 쓰는 표</td></tr>
 *   <tr><td>{@code api}</td><td>{@code db/migration/api}</td><td>{@code flyway_schema_history_api}</td><td>공개 사이트만 쓰는 새 표</td></tr>
 *   <tr><td>{@code admin}</td><td>{@code db/migration/admin}</td><td>{@code flyway_schema_history_admin}</td><td>관리자 앱만 쓰는 새 표</td></tr>
 * </table>
 *
 * <p><b>왜 이력 표를 갈랐나</b>: 이력 표 하나에 위치를 셋 두면 앱마다 일부 위치만 보게 되어 다른 쪽이 적용한 기록이 "해석되지 않는 이력"으로 보이고,
 * 번호가 어긋나면 {@code outOfOrder}·{@code missing} 검사를 꺼야 한다. 이력 표가 따로면 번호는 스트림 안에서만 이어지고, 검증은 엄격한 채로 남고,
 * 한쪽의 실수가 다른 앱의 기동을 막지 않는다.
 *
 * <p><b>이 클래스를 앱과 시험이 같이 쓴다</b> — 앱은 {@link SchemaMigrationConfig} 가 Boot 의 Flyway 자동 구성에 이 설정을 걸고, 시험은
 * {@link #migrate(DataSource, Collection)} 을 부른다. 그래서 "시험이 앱과 다르게 이관"하는 일이 없다.
 */
public final class SchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigration.class);

    /** 공통 스트림 이름 */
    public static final String SHARED = "shared";

    /** 앱과 시험이 쓰는 위치 — 스트림마다 {@code classpath:db/migration/<스트림>} 한 겹 아래 */
    public static final SchemaMigration STANDARD = new SchemaMigration("classpath:db/migration/");

    /** 스트림 이름은 위치 폴더와 이력 표 이름에 들어간다 — 소문자·숫자·밑줄만 */
    private static final Pattern STREAM_NAME = Pattern.compile("[a-z][a-z0-9_]{0,30}");

    private final String locationRoot;

    /**
     * @param locationRoot 스트림 폴더들이 있는 위치(끝에 {@code /}). 앱은 {@link #STANDARD} 만 쓴다 — 이 생성자는 표본 폴더를 가리키는 시험이 쓰려고 연다
     */
    public SchemaMigration(String locationRoot) {
        this.locationRoot = locationRoot;
    }

    /** 스트림의 마이그레이션 위치(클래스패스) */
    public String location(String stream) {
        return locationRoot + checked(stream);
    }

    /** 스트림의 이력 표. shared 는 Flyway 기본 이름({@code flyway_schema_history}) 그대로다 */
    public String historyTable(String stream) {
        return SHARED.equals(checked(stream)) ? "flyway_schema_history" : "flyway_schema_history_" + stream;
    }

    /**
     * 스트림 하나의 Flyway 설정을 건다 — Boot 의 자동 구성이 만드는 Flyway(shared)와 시험이 같은 값을 쓰도록 <b>이 한 곳에서만</b> 정한다.
     *
     * <p>shared 가 아닌 스트림에는 {@code baselineOnMigrate=true}·{@code baselineVersion=0} 를 건다: 그 스트림의 이력 표는 첫 파일이 생기는 날
     * <b>이미 표가 있는 DB 에 새로</b> 만들어지는데, Flyway 는 비어 있지 않은 스키마에 이력 표가 없으면 거부하고, baseline 을 켜도 기본 baselineVersion(1)이면
     * {@code V1} 이 "이미 적용됨"으로 건너뛰어진다. 0 이면 baseline 행(0) 뒤에 V1 부터 모두 적용된다.
     * shared 에는 걸지 않는다 — 빈 DB 에서는 baseline 이 필요 없고, 기존 DB 에는 이력 표가 이미 있다.
     */
    public FluentConfiguration configure(FluentConfiguration configuration, String stream) {
        configuration.locations(location(stream)).table(historyTable(stream));
        if (!SHARED.equals(stream)) {
            configuration.baselineOnMigrate(true).baselineVersion("0");
        }
        return configuration;
    }

    /** 스트림 하나의 Flyway */
    public Flyway flyway(DataSource dataSource, String stream) {
        return configure(Flyway.configure().dataSource(dataSource), stream).load();
    }

    /** shared → {@code ownStreams} 순서로 이관한다. 시험이 쓴다(앱은 Boot 의 Flyway 로 shared 를 이관하고 {@link #migrate(Flyway, Collection)} 을 부른다) */
    public void migrate(DataSource dataSource, Collection<String> ownStreams) {
        migrate(flyway(dataSource, SHARED), ownStreams);
    }

    /**
     * shared 스트림을 이관한 뒤 자기 스트림들을 이관한다.
     *
     * <p>이관은 Flyway 가 DB 잠금(PostgreSQL 어드바이저리 잠금, 이력 표 이름이 키)으로 한 프로세스만 적용하고 나머지는 기다렸다가 "적용할 것 없음"으로
     * 지나간다 — api 와 admin 이 같은 DB 에 동시에 떠도, shared 를 나란히 이관해도 된다. 스트림마다 잠금이 따로라 서로 기다리지 않는다.
     *
     * @param shared     shared 스트림으로 설정된 Flyway({@link #configure} 를 거친 것)
     * @param ownStreams 자기 스트림 이름들(예: {@code api}). 비어 있으면 shared 만 이관한다
     */
    public void migrate(Flyway shared, Collection<String> ownStreams) {
        int executed = shared.migrate().migrationsExecuted;
        log.info("마이그레이션 스트림 {}: {}건 적용({})", SHARED, executed, executed == 0 ? "적용할 것 없음" : "이관 끝");

        DataSource dataSource = shared.getConfiguration().getDataSource();
        for (String stream : ownStreams) {
            if (SHARED.equals(checked(stream))) {
                throw new IllegalArgumentException("shared 는 자기 스트림이 아니다: " + stream);
            }
            migrateOwn(dataSource, stream);
        }
    }

    /**
     * 자기 스트림 이관. <b>위치에 마이그레이션 파일이 하나도 없고 이력 표도 없으면 Flyway 를 실행하지 않는다</b> — {@code migrate()} 를 부르면 할 일이 없어도
     * {@code baselineOnMigrate} 때문에 빈 이력 표(+baseline 행)가 만들어지기 때문이다. 파일이 있으면 그 스트림의 이력 표는 첫 이관 때 만들어진다.
     */
    private void migrateOwn(DataSource dataSource, String stream) {
        Flyway flyway = flyway(dataSource, stream);
        // info() 는 이력 표를 만들지 않고 읽기만 한다. 해석된 파일도 적용된 기록도 없으면 비어 있다
        if (flyway.info().all().length == 0) {
            log.info("마이그레이션 스트림 {}: 파일이 없어 이관하지 않는다({})", stream, location(stream));
            return;
        }
        int executed = flyway.migrate().migrationsExecuted;
        log.info("마이그레이션 스트림 {}: {}건 적용({})", stream, executed, executed == 0 ? "적용할 것 없음" : "이관 끝");
    }

    private static String checked(String stream) {
        if (stream == null || !STREAM_NAME.matcher(stream).matches()) {
            throw new IllegalArgumentException("마이그레이션 스트림 이름이 올바르지 않다: " + stream);
        }
        return stream;
    }
}
