package com.community.testsupport;

import com.community.zconfiguration.jpa.JpaMasterConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 엔티티 픽스처가 같이 쓰는 트랜잭션.
 *
 * <ul>
 *   <li>시험이 연 트랜잭션이 없으면 한 번 부를 때마다 새 트랜잭션을 열고 끝나면 <b>커밋</b>한다 — 시험은 넣은 행을 다른 연결(MockMvc 요청·JdbcTemplate)로 읽는다.</li>
 *   <li>시험이 연 트랜잭션 안에서 부르면 거기에 참여하고, 끝에 {@code flush} 해 바로 SQL 을 내보낸다 — 예전 {@code jdbc.update} 처럼 같은 트랜잭션의 다음 JDBC 문장
 *       (외래 키로 이 행을 가리키는 INSERT 등)이 행을 본다.</li>
 *   <li>읽기도 매번 새 트랜잭션(또는 참여)이라 1차 캐시에 남은 옛 상태를 보지 않는다.</li>
 * </ul>
 */
final class FixtureTx {

    private final TransactionTemplate tx;
    private final EntityManager entityManager;

    FixtureTx(ApplicationContext context) {
        this.tx = new TransactionTemplate(context.getBean(JpaMasterConfig.TRANSACTION_MANAGER, PlatformTransactionManager.class));
        this.entityManager = SharedEntityManagerCreator.createSharedEntityManager(context.getBean(JpaMasterConfig.ENTITY_MANAGER, EntityManagerFactory.class));
    }

    void run(Runnable work) {
        tx.executeWithoutResult(status -> {
            work.run();
            entityManager.flush();
        });
    }

    <T> T call(Supplier<T> work) {
        return tx.execute(status -> work.get());
    }

    /** 픽스처가 엔티티에 넘기는 「지금」 — DB 가 마이크로초로 반올림하므로 처음부터 맞춘다 */
    static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
