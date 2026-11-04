package com.community.analytics.store;

import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 몰아쓰기의 「여러 줄을 한 문장으로」 도우미 — 칸마다 배열 하나를 {@code unnest(?::date[], ?::text[], …)} 에 넘긴다. 줄 수와 상관없이 문장 하나다.
 *
 * <p>값은 모두 글자 배열({@code text[]})로 넘기고 SQL 에서 칸 타입으로 바꾼다({@code ?::bigint[]}·{@code ?::timestamptz[]} …) — 드라이버의 타입별 배열 차이를 타지 않는다.
 * 시각은 {@link java.time.Instant#toString()}(UTC ISO), 날짜는 {@link java.time.LocalDate#toString()}, 불리언은 {@code true}/{@code false}, null 은 SQL NULL.
 */
public final class SqlArrays {

    private SqlArrays() {
    }

    public static Array text(Connection connection, List<?> values) throws SQLException {
        String[] out = new String[values.size()];
        for (int i = 0; i < out.length; i++) {
            Object v = values.get(i);
            out[i] = v == null ? null : v.toString();
        }
        return connection.createArrayOf("text", out);
    }
}
