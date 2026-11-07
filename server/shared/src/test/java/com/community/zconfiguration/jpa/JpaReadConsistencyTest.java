package com.community.zconfiguration.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditLogEntity;
import com.community.audit.entity.AuditResult;
import com.community.audit.repository.master.AuditLogRepository;
import com.community.holiday.entity.HolidaySource;
import com.community.holiday.entity.QSiteHolidayEntity;
import com.community.holiday.entity.SiteHolidayEntity;
import com.community.holiday.repository.master.SiteHolidayRepository;
import com.community.testsupport.JpaTestContext;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JPA 전환 보강(실제 PostgreSQL) — ① 예전 한 문장이 여러 문장이 된 읽기를 감싸는 읽기 전용 {@code REPEATABLE READ} 가 실제로 걸려 한 스냅숏이 되는지,
 * ② 유일 위반이 저장소·커밋 어느 쪽에서 나도 {@code JdbcTemplate} 때와 같은 {@link DuplicateKeyException} 인지({@link JpaMasterConfig} 의 방언 변환),
 * ③ {@link Ilike} 가 예전 {@code ILIKE … ESCAPE '\'} 와 같은 뜻인지(대소문자 무시, %·_·\ 는 글자 그대로).
 */
class JpaReadConsistencyTest {

    private static final QSiteHolidayEntity H = QSiteHolidayEntity.siteHolidayEntity;
    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(947);

    private static JPAQueryFactory query;
    private static SiteHolidayRepository holidays;
    private static AuditLogRepository audits;
    private static JdbcTemplate jdbc;
    private static String tag;
    private static LocalDate day;

    @BeforeAll
    static void setUp() {
        query = JpaTestContext.query();
        holidays = JpaTestContext.context().getBean(SiteHolidayRepository.class);
        audits = JpaTestContext.context().getBean(AuditLogRepository.class);
        jdbc = new JdbcTemplate(JpaTestContext.dataSource());
        tag = "zz-jpa-" + UUID.randomUUID().toString().substring(0, 8);
        day = LocalDate.of(2990, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(3000)); // 다른 시험의 쉬는 날과 겹치지 않는 먼 날
    }

    @AfterAll
    static void tearDown() {
        jdbc.update("DELETE FROM site_holidays WHERE name LIKE ?", tag + "%");
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** 다른 연결이 커밋한다(바깥 트랜잭션을 잠시 내려놓고 새 트랜잭션 — 연결이 다르다) */
    private static void commitElsewhere(String name) {
        TransactionTemplate other = new TransactionTemplate(JpaTestContext.transactions());
        other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        other.executeWithoutResult(s -> holidays.persist(SiteHolidayEntity.create(day, name, HolidaySource.SEED, false, null, now())));
    }

    private static long tagged(String prefix) {
        Long n = query.select(H.count()).from(H).where(H.name.startsWith(prefix)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 두 문장 사이에 다른 연결이 커밋한 줄을 본다/안 본다 — 앞 숫자, 뒤 숫자, 트랜잭션의 격리 수준 */
    private static Object[] twoReads(TransactionTemplate tx, String prefix) {
        return tx.execute(s -> {
            long before = tagged(prefix);
            commitElsewhere(prefix + "-new");
            long after = tagged(prefix);
            String isolation = jdbc.queryForObject("SELECT current_setting('transaction_isolation')", String.class); // 같은 트랜잭션의 연결
            return new Object[] {before, after, isolation};
        });
    }

    @Test
    void readOnlyRepeatableReadTemplate_isOneSnapshot() {
        String prefix = tag + "-rr";
        TransactionTemplate rr = new TransactionTemplate(JpaTestContext.transactions());
        rr.setReadOnly(true);
        rr.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        Object[] r = twoReads(rr, prefix);
        assertThat(r[2]).isEqualTo("repeatable read");
        assertThat(r[1]).isEqualTo(r[0]); // 사이에 커밋된 줄을 보지 않는다
        assertThat(tagged(prefix)).isEqualTo((long) r[0] + 1); // 커밋은 됐다
    }

    @Test
    void defaultReadCommitted_seesCommitBetweenStatements() {
        String prefix = tag + "-rc";
        TransactionTemplate rc = new TransactionTemplate(JpaTestContext.transactions());
        rc.setReadOnly(true);
        Object[] r = twoReads(rc, prefix);
        assertThat(r[2]).isEqualTo("read committed");
        assertThat(r[1]).isEqualTo((long) r[0] + 1); // 대조 — 격리 수준을 올리지 않으면 두 문장이 다른 시점을 본다
    }

    /** {@code @Transactional(isolation = REPEATABLE_READ)} 로 단 서비스 메서드(운영의 프록시와 같은 경계) */
    static class Reader {
        @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
        public long[] read(String prefix) {
            long before = tagged(prefix);
            commitElsewhere(prefix + "-new");
            long after = tagged(prefix);
            String isolation = jdbc.queryForObject("SELECT current_setting('transaction_isolation')", String.class);
            return new long[] {before, after, "repeatable read".equals(isolation) ? 1 : 0};
        }
    }

    @Test
    void transactionalAnnotationIsolation_isApplied() {
        Reader reader = JpaTestContext.transactional(new Reader());
        long[] r = reader.read(tag + "-an");
        assertThat(r[2]).isEqualTo(1);
        assertThat(r[1]).isEqualTo(r[0]);
    }

    @Test
    void uniqueViolation_isDuplicateKey_fromRepositoryAndCommit() {
        TransactionTemplate tx = new TransactionTemplate(JpaTestContext.transactions());
        // 저장소(Spring Data 프록시 — 예외 변환) 안에서 바로 나가는 넣기(IDENTITY): 유일 (source, holiday_date, name)
        String name = tag + "-dup";
        tx.executeWithoutResult(s -> holidays.persist(SiteHolidayEntity.create(day, name, HolidaySource.SEED, false, null, now())));
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> holidays.persist(SiteHolidayEntity.create(day, name, HolidaySource.SEED, false, null, now()))))
                .isInstanceOf(DuplicateKeyException.class);
        // 커밋 때 내보내는 넣기(앱이 매기는 ID — persist 만 하고 flush 하지 않는다): 커밋에서 나는 PK 위반도 같은 예외
        long id = IDS.nextId();
        tx.executeWithoutResult(s -> audits.persist(audit(id)));
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> audits.persist(audit(id)))).isInstanceOf(DuplicateKeyException.class);
        // 저장소 안에서 flush 로 내보낸 넣기
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            audits.persist(audit(id));
            audits.flush();
        })).isInstanceOf(DuplicateKeyException.class);
        jdbc.update("DELETE FROM audit_logs WHERE id = ?", id);
    }

    private static AuditLogEntity audit(long id) {
        return AuditLogEntity.create(id, now(), AuditActorType.SYSTEM, null, "ZZ_JPA_TEST", AuditResult.SUCCESS, null, null, null, null, null);
    }

    @Test
    void ilike_matchesOldIlikeEscapeBackslash() {
        String p = tag + "-il-";
        TransactionTemplate tx = new TransactionTemplate(JpaTestContext.transactions());
        tx.executeWithoutResult(s -> {
            for (String n : new String[] {"Ab%c", "abXc", "A_b", "aXb", "C\\d", "Cd"}) {
                holidays.persist(SiteHolidayEntity.create(day, p + n, HolidaySource.PUBLIC_DATA, false, null, now()));
            }
        });
        assertThat(names(Ilike.contains(H.name, "AB%C"))).containsExactly(p + "Ab%c"); // 대소문자 무시, % 는 글자 그대로
        assertThat(names(Ilike.contains(H.name, "a_b"))).containsExactly(p + "A_b"); // _ 는 글자 그대로
        assertThat(names(Ilike.contains(H.name, "c\\d"))).containsExactly(p + "C\\d"); // \ 도 글자 그대로
        assertThat(names(Ilike.of(H.name, "%" + p.toUpperCase() + "a_b"))).containsExactlyInAnyOrder(p + "A_b", p + "aXb"); // 패턴의 _ 는 한 글자
        assertThat(Ilike.containsPattern("50%_\\")).isEqualTo("%50\\%\\_\\\\%");
    }

    private static java.util.List<String> names(BooleanExpression where) {
        return query.select(H.name).from(H).where(H.name.startsWith(tag), where).orderBy(H.name.asc()).fetch();
    }
}
