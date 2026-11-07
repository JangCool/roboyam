package com.community.permission.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.group.repository.master.GroupCategoryLockQueries;
import com.community.operator.repository.master.OperatorCertRequestQueries;
import com.community.operator.repository.master.OperatorIdentityLockQueries;
import com.community.permission.repository.master.BootstrapLockQueries;
import com.community.testsupport.JpaTestContext;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 서비스에서 저장소로 옮긴 관리자 앱의 트랜잭션 어드바이저리 잠금 넷 — 키가 그대로이고, 부르는 쪽 트랜잭션 동안 쥐고 있다가 커밋하면 풀리는지(실제 PostgreSQL).
 * 다른 연결(자동 확정)에서 {@code pg_try_advisory_xact_lock} 으로 잡아 본다 — 쥐고 있으면 false, 풀렸으면 true(그 문장이 끝나면 곧바로 풀린다).
 */
class AdvisoryLockQueriesTest {

    private static JdbcTemplate other;
    private static TransactionTemplate tx;

    @BeforeAll
    static void setUp() {
        other = new JdbcTemplate(new DelegatingDataSource(JpaTestContext.dataSource())); // 다른 데이터소스 객체 — 트랜잭션에 묶인 연결을 받지 않고 새 연결을 쓴다
        tx = new TransactionTemplate(JpaTestContext.transactions());
    }

    /** 다른 연결에서 이 키를 잡을 수 있는가 */
    private static boolean free(long key) {
        return Boolean.TRUE.equals(other.queryForObject("select pg_try_advisory_xact_lock(?)", Boolean.class, key));
    }

    private static boolean freePair(int lockClass, String key) {
        return Boolean.TRUE.equals(other.queryForObject("select pg_try_advisory_xact_lock(?, hashtext(?))", Boolean.class, lockClass, key));
    }

    private static void heldDuringTransaction(Runnable acquire, BooleanSupplier free) {
        assertThat(free.getAsBoolean()).isTrue();
        tx.executeWithoutResult(status -> {
            acquire.run();
            assertThat(free.getAsBoolean()).isFalse();
        });
        assertThat(free.getAsBoolean()).isTrue();
    }

    @Test
    void 기동_맞춤_잠금() {
        BootstrapLockQueries lock = JpaTestContext.create(BootstrapLockQueries.class);
        heldDuringTransaction(lock::acquire, () -> free(7_340_001L));
    }

    @Test
    void 운영자_식별_값_잠금() {
        OperatorIdentityLockQueries lock = JpaTestContext.create(OperatorIdentityLockQueries.class);
        heldDuringTransaction(lock::acquire, () -> free(7_340_003L));
    }

    @Test
    void 소모임_분류_잠금() {
        GroupCategoryLockQueries lock = JpaTestContext.create(GroupCategoryLockQueries.class);
        heldDuringTransaction(lock::acquire, () -> free(7_340_005L));
    }

    @Test
    void 비밀번호_재설정_요청_키_잠금() {
        OperatorCertRequestQueries lock = JpaTestContext.create(OperatorCertRequestQueries.class);
        String key = "email:" + System.nanoTime();
        heldDuringTransaction(() -> lock.lockKey(key), () -> freePair(7_340_004, key));
        // 다른 키는 막지 않는다
        tx.executeWithoutResult(status -> {
            lock.lockKey(key);
            assertThat(freePair(7_340_004, key + ":other")).isTrue();
        });
    }
}
