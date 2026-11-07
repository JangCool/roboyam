package com.community.testsupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * 행 잠금 세기를 다른 연결에서 확인하는 시험 도우미. 그 행을 {@code FOR <mode> NOWAIT} 로 잡아 보고 막히면(55P03) true — 잡았으면 바로 되돌린다.
 *
 * <ul>
 *   <li>{@link #keyShareBlocked} 가 true 면 {@code FOR UPDATE} 다 — 그 행을 FK 로 가리키는 INSERT 의 확인({@code FOR KEY SHARE})까지 막는다.</li>
 *   <li>JPA {@code PESSIMISTIC_WRITE}({@code FOR NO KEY UPDATE})는 {@code KEY SHARE} 를 막지 않고 {@code NO KEY UPDATE} 를 막는다.</li>
 * </ul>
 */
public final class RowLockProbe {

    private RowLockProbe() {
    }

    /** {@code FOR KEY SHARE NOWAIT} 가 막히는가 — 막히면 그 행은 {@code FOR UPDATE} 로 잠겨 있다 */
    public static boolean keyShareBlocked(DataSource dataSource, String table, String where, Object... args) {
        return blocked(dataSource, "KEY SHARE", table, where, args);
    }

    /** {@code FOR NO KEY UPDATE NOWAIT} 가 막히는가 — 막히면 그 행은 쓰기 잠금({@code FOR NO KEY UPDATE} 이상)으로 잠겨 있다 */
    public static boolean noKeyUpdateBlocked(DataSource dataSource, String table, String where, Object... args) {
        return blocked(dataSource, "NO KEY UPDATE", table, where, args);
    }

    /** 다른 연결에서 {@code SELECT 1 FROM <table> WHERE <where> FOR <mode> NOWAIT} — 막히면(55P03) true */
    public static boolean blocked(DataSource dataSource, String mode, String table, String where, Object... args) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM " + table + " WHERE " + where + " FOR " + mode + " NOWAIT")) {
                for (int i = 0; i < args.length; i++) {
                    statement.setObject(i + 1, args[i]);
                }
                statement.executeQuery().close();
                return false;
            } catch (SQLException e) {
                if ("55P03".equals(e.getSQLState())) {
                    return true;
                }
                throw new IllegalStateException(e);
            } finally {
                connection.rollback();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
