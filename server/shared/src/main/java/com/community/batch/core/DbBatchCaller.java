package com.community.batch.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DB 배치(저장 프로시저)를 앱이 직접 돌린다 — 요청 표의 「지금 실행」을 배치를 켠 서버가 집었을 때 같은 프로시저를 {@code CALL} 한다(pg_cron 이 부르는 것과 같은 프로시저).
 *
 * <p>Native SQL 인 이유: {@code CALL} 은 PostgreSQL 전용이라 JPQL·QueryDSL 에 대응 문법이 없다. 프로시저 이름은 배치 키에서 만들고({@link BatchDefinition#procedureName}) 값은 바인딩한다.
 * <b>트랜잭션 밖의 새 연결</b>(자동 커밋)에서 돌린다 — 프로시저가 덩어리마다 COMMIT 하므로 이미 열린 트랜잭션 안에서는 실행할 수 없다.
 */
@Component
public class DbBatchCaller {

    private static final Logger log = LoggerFactory.getLogger(DbBatchCaller.class);

    /** Java 배치 잠금({@link BatchLockService})과 같은 어드바이저리 잠금 이름공간 — 같은 키면 Java·DB 어느 쪽이 돌든 서로를 막는다 */
    private static final int LOCK_NAMESPACE = 7_340_002;

    private final DataSource dataSource;

    public DbBatchCaller(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 저장 프로시저를 {@code CALL} 하고 끝날 때까지 기다린다. 돌려주는 값은 프로시저가 만든 로그 줄 ID 이고, 같은 배치가 이미 돌고 있으면 프로시저가 아무것도 하지 않고
     * null 을 돌려준다. 어드바이저리 잠금은 프로시저가 끝에서 푼다 — 오류로 끊기면 이 연결의 잠금을 풀고 예외를 그대로 던진다(풀 연결에 잠금이 남지 않게).
     *
     * @param operatorId 요청한 운영자(없으면 null)
     */
    public Long call(BatchDefinition definition, Long operatorId) throws SQLException {
        String procedure = definition.procedureName();
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getAutoCommit()) {
                connection.setAutoCommit(true);
            }
            try (PreparedStatement statement = connection.prepareStatement("call " + procedure + "('MANUAL', ?, NULL::bigint)")) {
                statement.setObject(1, operatorId, Types.BIGINT);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return null;
                    }
                    long runId = result.getLong(1);
                    return result.wasNull() ? null : runId;
                }
            } catch (SQLException e) {
                unlock(connection, definition.key()); // 프로시저가 끝맺지 못했다 — 쥔 채로 풀에 돌아가지 않게 풀어 준다(정상이면 프로시저가 batch_run_close 에서 이미 풀었다)
                throw e;
            }
        }
    }

    private static void unlock(Connection connection, String jobKey) {
        try (PreparedStatement statement = connection.prepareStatement("select pg_advisory_unlock(?, hashtext(?))")) {
            statement.setInt(1, LOCK_NAMESPACE);
            statement.setString(2, jobKey);
            statement.execute();
        } catch (SQLException e) {
            log.warn("DB 배치 잠금을 풀지 못했다 jobKey={}", jobKey, e);
            try (PreparedStatement all = connection.prepareStatement("select pg_advisory_unlock_all()")) {
                all.execute();
            } catch (SQLException ignored) {
                log.error("DB 배치 잠금을 모두 풀지 못했다 jobKey={}", jobKey);
            }
        }
    }
}
