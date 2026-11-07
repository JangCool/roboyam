package com.community.emoticon.repository.master;

import com.community.zconfiguration.jpa.JpaStoreWrites;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 로보티콘 저장소의 공통 바탕 — QueryDSL(질의 팩토리) + 새 줄 넣기(엔티티 {@code persist} 직후 {@code flush}) + 사유가 있어 남긴 네이티브 문장(JDBC). 공구 저장소의 바탕
 * ({@code GroupBuyStoreSupport})과 같은 규칙이다.
 *
 * <ul>
 *   <li><b>읽기는 한 줄 모양(프로젝션)</b>이다 — 엔티티를 영속성 문맥에 올리지 않으므로 같은 트랜잭션의 조건부 UPDATE(QueryDSL DML · JDBC)가 바꾼 값을 늘 DB 에서 다시 읽는다.</li>
 *   <li><b>쓰기는 즉시 실행</b>이다 — 상태 전이·카운터는 QueryDSL DML({@code execute()} 가 바로 돈다), 넣기는 {@code persist} 뒤 바로 {@code flush} 하고 문맥에서 뗀다
 *       ({@code detach} — 판의 차례처럼 지우고 같은 키로 다시 넣는 줄이 문맥의 옛 엔티티와 부딪히지 않게). 변경 감지에 맡기지 않는다.</li>
 *   <li><b>행 잠금</b>은 루트 표만 고르는 질의에 {@code PESSIMISTIC_WRITE}(PostgreSQL {@code FOR NO KEY UPDATE}) · {@code PESSIMISTIC_READ}({@code FOR SHARE}) — 잠그는 문장과 읽는
 *       문장을 나눈다(조인 프로젝션에 잠금을 걸면 잠기지 않는다). 잠그는 행을 외래 키로 가리키는 줄은 그 행을 먼저 잠근 흐름이나 그 행을 만드는 트랜잭션이 넣으므로
 *       {@code FOR UPDATE} 로 그 INSERT 까지 막지 않아도 결과가 같다.</li>
 *   <li>JDBC({@link #jdbc}) 문장은 같은 트랜잭션의 연결을 쓴다(JPA 트랜잭션 관리자가 연결을 묶는다). 사유는 문장마다 주석으로 적는다.</li>
 *   <li>시각은 서비스가 넘기는 {@code now} 다(시험이 시계를 옮긴다 — {@code DB_NOW} 로 바꾸지 않는다).</li>
 * </ul>
 */
abstract class EmoticonStoreSupport extends RoboyamRepositoryQDSL {

    protected final EntityManager entityManager;
    protected final JdbcTemplate jdbc;

    protected EmoticonStoreSupport(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query);
        this.entityManager = entityManager;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 새 줄 — 넣고 바로 내보낸 뒤 문맥에서 뗀다. 유일 색인에 걸리면 예전 JDBC 와 같은 {@link DuplicateKeyException}, 다른 제약 위반은 {@link DataIntegrityViolationException} */
    protected void insert(Object entity) {
        insertAll(List.of(entity));
    }

    /** 여러 새 줄 — 차례대로 넣고 한 번 내보낸다 */
    protected void insertAll(Collection<?> entities) {
        try {
            entities.forEach(entityManager::persist);
            entityManager.flush();
        } catch (RuntimeException e) {
            throw translate(e);
        }
        entities.forEach(entityManager::detach);
    }

    /** 제약 위반을 JDBC 때와 같은 스프링 예외로(SQLState 23505 = 유일 위반 → {@link DuplicateKeyException}, 다른 23xxx → {@link DataIntegrityViolationException}) */
    private static RuntimeException translate(RuntimeException e) {
        return JpaStoreWrites.translate(e); // 공구·사업자·광고 저장소와 같은 한 규칙
    }

    /** {@code sum(식)} — Hibernate 는 정수 합을 {@code Long} 으로 돌려준다. 없으면 null */
    static NumberExpression<Long> sumLong(Expression<? extends Number> value) {
        return Expressions.numberTemplate(Long.class, "sum({0})", value);
    }

    /** {@code count(*)} 를 Long 으로 */
    static long n(Long value) {
        return value == null ? 0 : value;
    }

    static List<String> list(String[] values) {
        return values == null ? List.of() : List.copyOf(Arrays.asList(values));
    }

    static Timestamp ts(Instant at) {
        return at == null ? null : Timestamp.from(at);
    }
}
