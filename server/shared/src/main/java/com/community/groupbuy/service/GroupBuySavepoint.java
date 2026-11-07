package com.community.groupbuy.service;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

/**
 * 결제 확정 트랜잭션 안의 공구 일을 <b>JDBC 세이브포인트</b>로 감싼다 — {@code onPaid} 는 도메인 사정으로 예외를 던지지 않고 REJECT 로 바꿔야 하는데(결정 62),
 * PostgreSQL 은 실패한 문장 뒤 트랜잭션 전체를 못 쓰게 하므로 그 일만 되돌릴 자리가 필요하다. 스프링 {@code PROPAGATION_NESTED} 는 JPA 트랜잭션 관리자에서 쓰지 못해
 * 연결을 직접 쓴다({@code IsolatedSideWork} 와 같은 방식).
 *
 * <p>영속성 컨텍스트와의 약속(3단계 1차 — 공구 저장소가 JPA·QueryDSL 이 된 뒤):
 * <ul>
 *   <li>세이브포인트를 잡기 <b>전에</b> 밀린 변경을 내보낸다({@code flush}) — 부르는 쪽(결제 모듈)의 변경이 세이브포인트 안에서 내보내졌다가 함께 되돌아가는 일을 막는다.</li>
 *   <li>안에서 도는 공구 쓰기는 모두 <b>즉시 실행</b>이다 — 조건부 상태 전이는 QueryDSL DML, 새 줄은 {@code persist} 직후 {@code flush}, 수량 열은 JDBC {@code UPDATE … RETURNING}.
 *       변경 감지에 맡긴 쓰기가 없으므로 되돌린 뒤 영속성 컨텍스트에 남아 나중에 내보내질 변경도 없다(읽기는 한 줄 모양이라 엔티티를 올리지 않는다).</li>
 *   <li>안에서 실패할 수 있는 문장(수량 조건부 UPDATE — 조건에 지면 0 행이지만 CHECK 를 건드릴 수 있는 수량 열)은 JDBC 로 둔다 — 세이브포인트 안에서 JPA 질의가 실패하면
 *       업무 트랜잭션 전체가 롤백 전용이 된다(2026-11-07 실측). 안의 JPA 문장(주문 조건부 전이·이력·이동 줄 넣기·다 팔림 마감·읽기)은 0 행으로 지거나 새 키·CHECK 를 지키는 값만 넣는다.</li>
 * </ul>
 */
@Component
public class GroupBuySavepoint {

    private final DataSource dataSource;
    private final EntityManager entityManager;

    public GroupBuySavepoint(DataSource dataSource, EntityManager entityManager) {
        this.dataSource = dataSource;
        this.entityManager = entityManager;
    }

    /**
     * 일을 세이브포인트 안에서 한다. 일이 던지면 세이브포인트로 되돌리고 그 예외를 다시 던진다(트랜잭션은 계속 쓸 수 있다).
     *
     * @throws IllegalStateException 트랜잭션 밖에서 불렀다
     */
    public <T> T run(Supplier<T> work) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            if (!DataSourceUtils.isConnectionTransactional(connection, dataSource)) {
                throw new IllegalStateException("세이브포인트는 트랜잭션 안에서만 쓴다");
            }
            entityManager.flush(); // 부르는 쪽의 밀린 변경을 먼저 — 세이브포인트 안에서 내보냈다가 되돌리지 않게
            Savepoint savepoint = connection.setSavepoint();
            T result;
            try {
                result = work.get();
            } catch (RuntimeException e) {
                connection.rollback(savepoint);
                throw e;
            }
            connection.releaseSavepoint(savepoint);
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("세이브포인트를 다루지 못했다", e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
