package com.community.batch.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 배치 하나를 서버 여러 대 중 <b>한 곳에서만</b> 돌리는 DB 어드바이저리 잠금(세션 잠금).
 *
 * <p>배치가 도는 내내 연결 하나를 쥐고 있다가 끝나면 풀고({@link BatchLock#close()}) 돌려준다. 서버가 죽으면 DB 가 연결을 끊으면서 잠금도 풀리므로 영영 잠기는 일이 없다.
 * 잠금은 실행 중에만 서로를 막는다 — 같은 예약 시각(슬롯)의 중복 실행은 {@code batch_runs} 의 유일 색인이 막는다(둘은 이중 방어).
 *
 * <p>Native SQL 인 이유: {@code pg_try_advisory_lock} 은 PostgreSQL 전용이라 JPQL·QueryDSL 에 대응 문법이 없고, 세션 잠금은 같은 연결에서 풀어야 해서 트랜잭션 연결이 아니라 연결을 직접 쥔다.
 */
@Component
public class BatchLockService {

    private static final Logger log = LoggerFactory.getLogger(BatchLockService.class);

    /** 이 앱의 다른 어드바이저리 잠금({@code BootstrapLock})과 겹치지 않게 정한 고정 값 — 두 정수 키 형태의 앞 키 */
    private static final int NAMESPACE = 7_340_002;

    private final DataSource dataSource;

    public BatchLockService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 잠금을 시도한다. 다른 곳이 쥐고 있으면(그 배치가 돌고 있으면) 비어 있다. 잡았으면 {@code try-with-resources} 로 푼다 */
    public Optional<BatchLock> tryAcquire(String jobKey) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            try (PreparedStatement statement = connection.prepareStatement("select pg_try_advisory_lock(?, hashtext(?))")) {
                statement.setInt(1, NAMESPACE);
                statement.setString(2, jobKey);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next() && result.getBoolean(1)) {
                        return Optional.of(new BatchLock(connection, jobKey));
                    }
                }
            }
            connection.close();
            return Optional.empty();
        } catch (SQLException e) {
            closeQuietly(connection);
            throw new IllegalStateException("배치 잠금을 시도하지 못했다: " + jobKey, e);
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // 이미 실패를 알리는 중이다
            }
        }
    }

    /** 잡은 잠금. 풀지 않으면 연결 풀의 그 연결에 잠금이 남으므로 반드시 닫는다 */
    public static final class BatchLock implements AutoCloseable {

        private final Connection connection;
        private final String jobKey;

        private BatchLock(Connection connection, String jobKey) {
            this.connection = connection;
            this.jobKey = jobKey;
        }

        @Override
        public void close() {
            try {
                boolean released = false;
                try (PreparedStatement statement = connection.prepareStatement("select pg_advisory_unlock(?, hashtext(?))")) {
                    statement.setInt(1, NAMESPACE);
                    statement.setString(2, jobKey);
                    try (ResultSet result = statement.executeQuery()) {
                        released = result.next() && result.getBoolean(1);
                    }
                } catch (SQLException e) {
                    log.warn("배치 잠금을 풀지 못했다 jobKey={}", jobKey, e);
                }
                if (!released) {
                    // 풀리지 않은 잠금이 풀에 돌아가 다음 실행을 막지 않게 이 연결의 잠금을 모두 푼다
                    releaseAll();
                }
            } finally {
                closeQuietly(connection);
            }
        }

        private void releaseAll() {
            try (PreparedStatement statement = connection.prepareStatement("select pg_advisory_unlock_all()")) {
                statement.execute();
            } catch (SQLException e) {
                log.error("배치 잠금을 모두 풀지 못했다 jobKey={}", jobKey, e);
            }
        }
    }
}
