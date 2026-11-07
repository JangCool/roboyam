package com.community.zconfiguration.jpa;

import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/**
 * 유일 색인 위반({@code 23505}) 다루기 — JDBC({@code JdbcTemplate})에서 JPA 로 옮긴 저장소가 「넣기의 유일 위반 = {@link DuplicateKeyException}」 약속을 그대로 지키게 한다.
 * JPA 리포지토리의 예외 변환은 유일 위반도 {@link DataIntegrityViolationException} 으로만 주므로, 넣고 바로 내보낸 자리에서 원인을 보고 바꾼다(다른 무결성 위반은 그대로).
 */
public final class UniqueViolations {

    private UniqueViolations() {
    }

    /** 유일 색인 위반인가 — 원인을 따라가 본다 */
    public static boolean is(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 넣기(persist + flush)를 돌린다 — 유일 위반이면 {@link DuplicateKeyException}(JDBC 때와 같은 예외), 그 밖의 무결성 위반은 그대로 던진다.
     */
    public static void inserting(Runnable insert) {
        try {
            insert.run();
        } catch (DuplicateKeyException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            if (is(e)) {
                throw new DuplicateKeyException(e.getMostSpecificCause().getMessage(), e);
            }
            throw e;
        }
    }
}
