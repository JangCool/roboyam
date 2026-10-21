package com.community.zconfiguration.flyway;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 앱(api · admin)이 기동할 때 <b>JPA 보다 먼저</b> shared → 자기 스트림 순서로 스키마를 이관하게 이어 준다({@link SchemaMigration}).
 *
 * <p><b>Boot 의 Flyway 자동 구성을 끄지 않고 그 안에 건다</b>(결정 근거):
 * <ul>
 *   <li>자동 구성이 만든 {@code FlywayMigrationInitializer} 를 Boot 가 "데이터베이스 초기화"로 알아보고 {@code EntityManagerFactory} 가 그것에 의존하게 한다
 *       — 이관이 끝난 뒤에야 JPA 의 스키마 검증·엔티티 매핑이 시작된다. 둘째 스트림도 이 안(전략)에서 이관하므로 같은 순서가 보장된다.</li>
 *   <li>{@code Flyway} 빈이 계속 하나(shared 스트림)라 스키마 버전·적용 대기를 읽는 코드가 shared 스트림 기준으로 읽는다.
 *       자기 스트림의 대기는 그 앱의 기동 실패로 드러난다.</li>
 *   <li>직접 {@code Flyway} 빈 둘을 만들면 Boot 의 자동 구성이 물러나고 순서 보장도 직접 해야 한다 — 서버 시작 경로를 바꾸는 위험을 키운다.</li>
 * </ul>
 * 위치·이력 표는 yml 의 {@code spring.flyway.locations} 가 아니라 여기(코드)에서 정한다 — 시험이 같은 값을 쓰도록 한 곳에서만.
 *
 * <p>자기 스트림 이름은 앱의 {@code application.yml} 이 {@code roboyam.schema.stream} 으로 정한다(api → {@code api}, admin → {@code admin}).
 * 비어 있으면 shared 만 이관한다.
 *
 * <p>주의: 앱 진입점의 스캔 범위에 {@code com.community.zconfiguration} 이 들어와야 이 설정이 적용된다.
 */
@Configuration(proxyBeanMethods = false)
public class SchemaMigrationConfig {

    /** Boot 의 Flyway 가 shared 스트림(위치·이력 표)으로 설정되게 한다 */
    @Bean
    FlywayConfigurationCustomizer sharedSchemaStream() {
        return configuration -> SchemaMigration.STANDARD.configure(configuration, SchemaMigration.SHARED);
    }

    /** shared 를 이관한 뒤 자기 스트림을 이관한다 — 자동 구성의 초기화 단계에서 불리므로 JPA 보다 먼저다 */
    @Bean
    FlywayMigrationStrategy schemaMigrationStrategy(@Value("${roboyam.schema.stream:}") String ownStream) {
        List<String> ownStreams = ownStream == null || ownStream.isBlank() ? List.of() : List.of(ownStream.strip());
        return flyway -> SchemaMigration.STANDARD.migrate(flyway, ownStreams);
    }
}
