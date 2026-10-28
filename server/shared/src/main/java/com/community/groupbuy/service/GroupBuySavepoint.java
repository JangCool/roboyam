package com.community.groupbuy.service;

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
 * 연결을 직접 쓴다({@code IsolatedSideWork} 와 같은 방식 — 공구 쓰기는 모두 JDBC 라 영속성 컨텍스트를 내보낼 것이 없다).
 */
@Component
public class GroupBuySavepoint {

    private final DataSource dataSource;

    public GroupBuySavepoint(DataSource dataSource) {
        this.dataSource = dataSource;
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
