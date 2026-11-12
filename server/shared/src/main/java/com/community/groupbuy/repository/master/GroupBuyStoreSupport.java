package com.community.groupbuy.repository.master;

import com.community.zconfiguration.jpa.JpaStoreWrites;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 공구 저장소의 공통 바탕 — QueryDSL(질의 팩토리) + 새 줄 넣기(엔티티 {@code persist} 직후 {@code flush}) + 사유가 있어 남긴 네이티브 문장(JDBC).
 *
 * <ul>
 *   <li><b>읽기는 한 줄 모양(프로젝션)</b>이다 — 엔티티를 영속성 컨텍스트에 올리지 않으므로 같은 트랜잭션의 조건부 UPDATE(QueryDSL DML · JDBC)가 바꾼 값을 늘 DB 에서 다시 읽는다.</li>
 *   <li><b>쓰기는 즉시 실행</b>이다 — 상태 전이·카운터는 QueryDSL DML({@code execute()} 가 바로 돈다), 넣기는 {@code persist} 뒤 바로 {@code flush}. 변경 감지에 맡기지 않는다
 *       (뒤따르는 JDBC 문장이 그 줄을 FK 로 보거나 읽어도 된다). 단 결제 확정의 JDBC 세이브포인트 안에서 도는 쓰기는 JDBC 로 둔다 — 그 안에서 JPA 문장이 실패하면
 *       업무 트랜잭션 전체가 롤백 전용이 되어 거절로 되돌릴 수 없다(G25 ③ — {@code GroupBuySavepoint}).</li>
 *   <li><b>행 잠금</b>은 루트 표만 고르는 id 문장의 {@code PESSIMISTIC_WRITE}(PostgreSQL {@code FOR NO KEY UPDATE}) — 잠그는 문장과 읽는 문장을 나눈다(조인 프로젝션에
 *       잠금을 걸면 잠기지 않는다). 잠그는 행을 FK 로 가리키는 줄은 그 행을 먼저 잠그거나 고치는(조건부 UPDATE) 흐름이 넣으므로 그 INSERT 까지 막지 않아도 결과가 같다 —
 *       아닌 곳(상품 행)만 사유를 적은 네이티브 {@code FOR UPDATE}. 잠금 차례는 commerce.md 결정 37(A→G→O→X) 그대로다.</li>
 *   <li>JDBC({@link #jdbc}) 문장은 같은 트랜잭션의 연결을 쓴다(JPA 트랜잭션 관리자가 연결을 묶는다). 사유는 문장마다 주석으로 적는다.</li>
 * </ul>
 */
abstract class GroupBuyStoreSupport extends IrobitRepositoryQDSL {

    protected final EntityManager entityManager;
    protected final JdbcTemplate jdbc;

    protected GroupBuyStoreSupport(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query);
        this.entityManager = entityManager;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 새 줄 — 넣고 바로 내보낸다(뒤의 JDBC 문장·세이브포인트가 그 줄을 본다). 유일 색인에 걸리면 예전 JDBC 와 같은 {@link DuplicateKeyException} 으로 올린다
     * (서비스가 그것을 잡아 「이미 있다」로 바꾼다 — 진행 중 주문·열린 요청·승인 대기·차수 번호의 부분 유일 색인, 결정 66).
     */
    protected void insert(Object entity) {
        try {
            entityManager.persist(entity);
            entityManager.flush();
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    /** 여러 새 줄 — 다 넣고 한 번 내보낸다 */
    protected void insertAll(Collection<?> entities) {
        try {
            entities.forEach(entityManager::persist);
            entityManager.flush();
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    /** 제약 위반을 JDBC 때와 같은 스프링 예외로(SQLState 23505 = 유일 위반 → {@link DuplicateKeyException}, 다른 23xxx → {@link DataIntegrityViolationException}) */
    private static RuntimeException translate(RuntimeException e) {
        return JpaStoreWrites.translate(e); // 사업자·광고·이모티콘 저장소와 같은 한 규칙
    }

    /** {@code sum(식)} — Hibernate 는 정수 합을 {@code Long} 으로 돌려준다(QueryDSL 의 {@code sum()} 은 칸 타입 그대로라 받을 때 어긋난다). 없으면 null */
    static NumberExpression<Long> sumLong(Expression<? extends Number> value) {
        return Expressions.numberTemplate(Long.class, "sum({0})", value);
    }

    static Long[] array(List<Long> values) {
        return values.toArray(Long[]::new);
    }

    static List<Long> list(Long[] values) {
        return values == null ? List.of() : List.copyOf(Arrays.asList(values));
    }

    static int n(Integer value) {
        return value == null ? 0 : value;
    }

    static long n(Long value) {
        return value == null ? 0 : value;
    }
}
