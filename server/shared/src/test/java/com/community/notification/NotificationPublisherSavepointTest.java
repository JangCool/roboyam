package com.community.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.repository.master.NotificationDeliveryRepository;
import com.community.notification.repository.master.NotificationPublishReads;
import com.community.notification.service.NotificationPublisher;
import com.community.operator.entity.QOperatorEntity;
import com.community.testsupport.CommerceTestDatabase;
import com.community.testsupport.JpaTestWiring;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.JpaMasterConfig;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 알림 발행기의 「알림 실패가 업무를 깨지 않는다」(notification.md 2-3) — 실제 PostgreSQL + 운영과 같은 JPA({@link JpaTestWiring}).
 *
 * <p>세이브포인트 안에서 받는 사람 조회가 실패해도 업무(JPA 쓰기)는 커밋된다. 예전에는 이 조회가 QueryDSL 이라 실패하면 업무 트랜잭션이 롤백 전용이 되어 커밋이 실패했다(2026-11-07 실측) —
 * 지금은 같은 연결의 JDBC({@link NotificationPublishReads})라 세이브포인트로 되돌리면 끝난다. 운영자 ID 는 시험마다 새로, 만든 행은 스스로 지운다.
 */
class NotificationPublisherSavepointTest {

    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(921);
    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;
    private static final AdminNotificationEvent EVENT = AdminNotificationEvent.GRADE_SETTINGS_SAVED; // 권한 보유자 + 최고 관리자, 묶기·이메일 없음

    /** 켜 두면 받는 사람 조회가 업무 트랜잭션 연결에서 실제 SQL 오류를 낸다(PostgreSQL 은 그 뒤 트랜잭션을 못 쓰게 만든다 — 세이브포인트로만 살아난다) */
    private static volatile boolean failRecipients;

    private static AnnotationConfigApplicationContext spring;
    private static JdbcTemplate jdbc;
    private static NotificationPublisher publisher;
    private static TransactionTemplate tx;
    private static JPAQueryFactory query;

    @Configuration(proxyBeanMethods = false)
    @Import(JpaTestWiring.class)
    static class Wiring {

        @Bean
        DataSource dataSource() {
            return CommerceTestDatabase.migrated();
        }

        @Bean
        NotificationPublishReads notificationPublishReads(DataSource dataSource) {
            JdbcTemplate same = new JdbcTemplate(dataSource);
            return new NotificationPublishReads(dataSource) {
                @Override
                public Set<Long> activeHolders(Collection<String> permissionCodes, Instant now) {
                    if (failRecipients) {
                        same.queryForObject("select 1 / 0", Integer.class);
                    }
                    return super.activeHolders(permissionCodes, now);
                }
            };
        }

        @Bean
        NotificationPublisher notificationPublisher(NotificationPublishReads reads, DataSource dataSource, EntityManager entityManager,
                                                    org.springframework.context.ApplicationContext context) {
            return new NotificationPublisher(reads, new NotificationDeliveryRepository(dataSource), IDS, context, entityManager, dataSource);
        }
    }

    @BeforeAll
    static void start() {
        spring = new AnnotationConfigApplicationContext(Wiring.class);
        jdbc = new JdbcTemplate(spring.getBean(DataSource.class));
        publisher = spring.getBean(NotificationPublisher.class);
        tx = new TransactionTemplate(spring.getBean(PlatformTransactionManager.class));
        query = spring.getBean(JpaMasterConfig.QUERY_FACTORY, JPAQueryFactory.class);
    }

    @AfterAll
    static void stop() {
        spring.close();
    }

    private static long newOperator(boolean locked) {
        long id = IDS.nextId();
        jdbc.update("INSERT INTO operators (id, email, operator_name, password_hash, is_root, approved, locked, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'x', false, true, ?, now(), now())", id, "publisher" + id + "@test.invalid", "op-" + Long.toString(id, 36), locked);
        return id;
    }

    /** 시험이 낸 알림만 가리키는 묶음 키 — 받는 사람에는 시험 DB 에 이미 있는 최고 관리자·권한 보유자도 들어가므로 그 행까지 이 키로 지운다 */
    private static String keyOf(long operatorId) {
        return "publisher-savepoint-" + operatorId;
    }

    private static void remove(long... operatorIds) {
        for (long id : operatorIds) {
            jdbc.update("DELETE FROM admin_notifications WHERE dedupe_key = ?", keyOf(id));
            jdbc.update("DELETE FROM admin_notifications WHERE operator_id = ?", id);
            jdbc.update("DELETE FROM operator_notification_mutes WHERE operator_id = ?", id);
            jdbc.update("DELETE FROM operators WHERE id = ?", id);
        }
    }

    private static int notifications(long operatorId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM admin_notifications WHERE operator_id = ? AND event_code = ?", Integer.class, operatorId, EVENT.code());
        return count == null ? 0 : count;
    }

    private static String nameOf(long operatorId) {
        return jdbc.queryForObject("SELECT operator_name FROM operators WHERE id = ?", String.class, operatorId);
    }

    /** 업무 = JPA(QueryDSL) 쓰기 한 번 + 마지막에 알림 발행(추가 수신자 = 시험 운영자들) */
    private static int businessThenPublish(long operatorId, String newName, long... alsoTo) {
        Integer created = tx.execute(status -> {
            query.update(OPERATOR).set(OPERATOR.operatorName, newName).where(OPERATOR.id.eq(operatorId)).execute();
            NotificationContext context = NotificationContext.empty().dedupe(keyOf(operatorId));
            for (long id : alsoTo) {
                context = context.alsoTo(id);
            }
            return publisher.publish(EVENT, context);
        });
        return created == null ? 0 : created;
    }

    @Test
    void 세이브포인트_안에서_수신자_조회가_실패해도_업무가_커밋되고_알림은_없다() {
        long operator = newOperator(false);
        failRecipients = true;
        try {
            int created = businessThenPublish(operator, "a-" + Long.toString(operator, 36), operator);

            assertThat(created).as("실패는 던지지 않고 0").isZero();
            assertThat(nameOf(operator)).as("업무 쓰기는 커밋됐다").isEqualTo("a-" + Long.toString(operator, 36));
            assertThat(notifications(operator)).isZero();
        } finally {
            failRecipients = false;
            remove(operator);
        }
    }

    @Test
    void 정상이면_활성_추가_수신자가_받고_개인_끄기와_잠긴_계정은_빠진다() {
        long operator = newOperator(false);
        long muted = newOperator(false);
        long locked = newOperator(true);
        jdbc.update("INSERT INTO operator_notification_mutes (operator_id, event_code, created_at) VALUES (?, ?, now())", muted, EVENT.code());
        try {
            int created = businessThenPublish(operator, "b-" + Long.toString(operator, 36), operator, muted, locked);

            assertThat(created).isPositive();
            assertThat(nameOf(operator)).isEqualTo("b-" + Long.toString(operator, 36));
            assertThat(notifications(operator)).isEqualTo(1);
            assertThat(notifications(muted)).as("개인 설정에서 끈 이벤트").isZero();
            assertThat(notifications(locked)).as("잠긴 운영자는 활성이 아니다").isZero();
        } finally {
            remove(operator, muted, locked);
        }
    }
}
