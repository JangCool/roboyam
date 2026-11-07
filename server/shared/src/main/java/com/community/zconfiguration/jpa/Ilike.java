package com.community.zconfiguration.jpa;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;

/**
 * 네이티브 SQL 의 {@code 칸 ILIKE ?} 를 QueryDSL 로 옮길 때 그대로 쓰는 식 — HQL {@code ilike}(PostgreSQL 에 {@code ILIKE} 로 내려간다), 탈출 글자 {@code \}.
 *
 * <p>QueryDSL 의 {@code containsIgnoreCase}·{@code likeIgnoreCase} 는 {@code lower(칸) like lower(?) escape '!'} 라 대소문자 접기·탈출 글자·탈 수 있는 색인이 예전
 * {@code ILIKE} 와 다르다 — 예전이 {@code ILIKE} 였던 검색은 이것을 쓴다(예전이 {@code lower(칸) LIKE} 였던 곳은 그대로 둔다 — {@code lower(칸)} 위의 trgm 색인을 탄다).
 */
public final class Ilike {

    private Ilike() {
    }

    /** {@code 칸 ILIKE 패턴 ESCAPE '\'} — 패턴은 부르는 쪽이 만든다(예전 SQL 에 넘기던 값 그대로) */
    public static BooleanExpression of(Expression<String> column, String pattern) {
        return Expressions.booleanTemplate("{0} ilike {1} escape '\\'", column, pattern);
    }

    /** 부분 일치 — 예전 {@code "%" + 말.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"} 와 같은 패턴(%·_·\ 는 글자 그대로) */
    public static BooleanExpression contains(Expression<String> column, String term) {
        return of(column, containsPattern(term));
    }

    /** 부분 일치 패턴 — 말의 {@code \}·{@code %}·{@code _} 를 {@code \} 로 탈출하고 양쪽에 {@code %} */
    public static String containsPattern(String term) {
        return "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
