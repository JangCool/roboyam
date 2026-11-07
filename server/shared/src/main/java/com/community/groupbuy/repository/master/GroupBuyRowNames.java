package com.community.groupbuy.repository.master;

import com.community.user.entity.UserStatus;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import java.time.Instant;

/**
 * 공구 질의가 여러 곳에서 같이 쓰던 SQL 식을 자바로 옮긴 것 — 저장소가 칸을 읽어 와 여기서 합친다(shared·admin·api 의 공구 저장소가 같이 쓴다).
 */
public final class GroupBuyRowNames {

    private GroupBuyRowNames() {
    }

    /** 예전 {@code COALESCE(NULLIF(g.title, ''), p.name)} — 공구 제목이 없거나 빈 글자면 상품 이름(공백만 있는 제목은 그대로다) */
    public static String title(String title, String productName) {
        return title == null || title.isEmpty() ? productName : title;
    }

    /**
     * 예전 {@code (u.id IS NULL OR u.status = 'WITHDRAWN' OR u.deleted_at IS NOT NULL)} — 왼쪽 조인한 회원 줄이 없거나 탈퇴·삭제됐다.
     *
     * @param joinedUserId 왼쪽 조인한 {@code users.id}(줄이 없으면 null)
     */
    public static boolean gone(Long joinedUserId, UserStatus status, Instant deletedAt) {
        return joinedUserId == null || status == UserStatus.WITHDRAWN || deletedAt != null;
    }

    /** ILIKE 찾기 말 — 예전과 같이 {@code %}·{@code _} 만 글자 그대로(ESCAPE '\'), 양쪽에 {@code %} */
    public static String like(String needle) {
        return "%" + needle.replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** 예전 {@code 칸 ILIKE ?} 그대로(HQL {@code ilike} → PostgreSQL {@code ILIKE}, 이스케이프 글자 {@code \}) — {@code lower() like} 로 바꾸지 않는다 */
    public static BooleanExpression ilike(Expression<String> column, String pattern) {
        return Expressions.booleanTemplate("{0} ilike {1} escape '\\'", column, pattern);
    }
}
