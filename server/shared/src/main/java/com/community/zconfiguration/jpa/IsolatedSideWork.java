package com.community.zconfiguration.jpa;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

/**
 * 업무 트랜잭션 안에서 <b>실패해도 업무를 깨면 안 되는 덧붙은 일</b>(회원 알림 행 만들기·포인트 적립/회수)을 JDBC 세이브포인트로 격리해 돌린다.
 *
 * <p>관리자 알림 발행기({@code NotificationPublisher})와 같은 방식이다: 업무 쪽 변경을 먼저 내보내고({@code flush} — 업무 오류가 여기서 삼켜지지 않게), 업무 트랜잭션이 쥔 연결에
 * 세이브포인트를 잡은 뒤 일을 한다. 실패하면 그 세이브포인트로 되돌리고 로그(예외 클래스 이름만 — 문구에 SQL 인자가 실릴 수 있다)만 남긴 뒤 {@code fallback} 을 돌려준다.
 * try-catch 만으로는 부족하다 — PostgreSQL 은 실패한 문장 뒤 트랜잭션 전체를 못 쓰게 해 업무 저장까지 날아간다. 그래서 안쪽 일은 JPA 영속성 컨텍스트를 거치지 않고 JDBC 로 한다.
 * 스프링 {@code PROPAGATION_NESTED} 는 JPA 트랜잭션 관리자에서 쓰지 못해 연결을 직접 쓴다. 트랜잭션 밖에서 부르면 되돌릴 수 없으므로 일을 하지 않고 {@code fallback} 이다.
 */
@Component
public class IsolatedSideWork {

    private static final Logger log = LoggerFactory.getLogger(IsolatedSideWork.class);

    private final DataSource dataSource;
    private final EntityManager entityManager;

    public IsolatedSideWork(DataSource dataSource, EntityManager entityManager) {
        this.dataSource = dataSource;
        this.entityManager = entityManager;
    }

    /**
     * @param label    로그에 남길 일 이름(값은 싣지 않는다)
     * @param fallback 실패했을 때 돌려줄 값
     */
    public <T> T run(String label, Supplier<T> work, T fallback) {
        entityManager.flush(); // 업무 쪽 변경을 먼저 — 그 실패는 원래 자리에서 올라와야 한다
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            if (!DataSourceUtils.isConnectionTransactional(connection, dataSource)) {
                log.error("업무 트랜잭션 밖이라 덧붙은 일을 하지 않았다 work={}", label);
                return fallback;
            }
            Savepoint savepoint = connection.setSavepoint();
            try {
                T result = work.get();
                connection.releaseSavepoint(savepoint);
                return result;
            } catch (RuntimeException e) {
                try {
                    connection.rollback(savepoint);
                } catch (SQLException rollbackFailure) {
                    log.error("세이브포인트를 되돌리지 못했다 work={} 원인={}", label, rollbackFailure.getClass().getSimpleName());
                }
                log.error("덧붙은 일을 하지 못했다 — 업무에는 영향이 없다 work={} 원인={}", label, e.getClass().getSimpleName());
                return fallback;
            }
        } catch (SQLException e) {
            log.error("세이브포인트를 만들지 못했다 work={} 원인={}", label, e.getClass().getSimpleName());
            return fallback;
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
