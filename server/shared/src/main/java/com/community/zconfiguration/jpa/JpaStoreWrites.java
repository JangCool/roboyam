package com.community.zconfiguration.jpa;

import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.Collection;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/**
 * JDBC 에서 JPA·QueryDSL 로 옮긴 저장소의 쓰기·잠금 도우미(사업자 신청 건·광고·사업자 표 — 공구의 {@code GroupBuyStoreSupport} 와 같은 규칙).
 *
 * <ul>
 *   <li><b>넣기는 즉시</b> — {@code persist} 뒤 바로 {@code flush}(뒤따르는 네이티브 문장·다른 모듈의 JDBC 가 그 줄을 FK 로 보거나 읽어도 된다). 제약 위반은 예전 JDBC 와 같은
 *       스프링 예외로(SQLState 23505 = 유일 위반 → {@link DuplicateKeyException}, 다른 23xxx → {@link DataIntegrityViolationException}) — 서비스가 그것을 잡아 409 로 바꾼다.</li>
 *   <li><b>행 잠금</b>은 루트 표만 고르는 id 프로젝션에 {@code PESSIMISTIC_WRITE}(PostgreSQL {@code FOR NO KEY UPDATE}) — 잠그는 문장과 읽는 문장을 나눈다
 *       (다른 표를 조인한 프로젝션에 잠금을 걸면 잠기지 않는다). 그 행을 FK 로 가리키는 INSERT 까지 막아야 결과가 같은 자리만 저장소의 네이티브 {@code FOR UPDATE}(사유 주석).</li>
 * </ul>
 */
public final class JpaStoreWrites {

    private JpaStoreWrites() {
    }

    /** 새 줄(들) — 넣고 한 번 내보낸다 */
    public static void insert(EntityManager entityManager, Object... entities) {
        try {
            for (Object entity : entities) {
                entityManager.persist(entity);
            }
            entityManager.flush();
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    /** 여러 새 줄 — 다 넣고 한 번 내보낸다 */
    public static void insertAll(EntityManager entityManager, Collection<?> entities) {
        insert(entityManager, entities.toArray());
    }

    /**
     * 손으로 부른 {@code persist}·{@code flush} 의 제약 위반을 {@code JdbcTemplate} 때와 같은 스프링 예외로 — SQLState 23505(유일 위반) → {@link DuplicateKeyException},
     * 다른 23xxx → {@link DataIntegrityViolationException}, 이미 스프링 예외면 그대로. 공구·이모티콘 저장소 바탕({@code GroupBuyStoreSupport}·{@code EmoticonStoreSupport})도
     * 이 한 규칙을 쓴다 — 저장소 프록시 없이 손으로 조립한 시험 문맥에서도 운영과 같은 예외가 나오게(운영은 {@link JpaMasterConfig} 의 방언 변환도 같은 규칙이다).
     */
    public static RuntimeException translate(RuntimeException e) {
        if (e instanceof DataAccessException) {
            return e;
        }
        for (Throwable c = e; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("23")) {
                return "23505".equals(sql.getSQLState()) ? new DuplicateKeyException(sql.getMessage(), e) : new DataIntegrityViolationException(sql.getMessage(), e);
            }
        }
        return e;
    }
}
