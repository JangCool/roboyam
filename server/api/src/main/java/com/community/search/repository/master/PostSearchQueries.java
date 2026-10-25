package com.community.search.repository.master;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 통합 검색({@code GET /api/search})의 글 찾기. <b>트랜잭션은 부르는 서비스가 연다</b>(시간 제한 {@link #limitTime} 이 그 트랜잭션에만 걸린다).
 *
 * <p><b>찾는 규칙</b>: 단어마다(AND) 범위의 칸 중 어디든 부분 일치·대소문자 무시({@code lower(칸) LIKE lower('%단어%')}, {@code % _ \} 는 글자 그대로 — 백슬래시로 가린다).
 * <ul>
 *   <li>{@code title} 제목 · {@code body} 본문 평문({@code coalesce(content_text, content)} — HTML 글은 태그를 뗀 평문 칸, 게시판 안 검색 {@code PostRepositoryQDSL#page} 와 같은 식)
 *       · {@code author} 회원 닉네임(profiles) 또는 비회원 이름 · {@code all} 셋 중 어디든.</li>
 *   <li>나오는 글: 주어진 게시판의 보이는 글(지움·임시저장·예약 삭제 시각 지남 제외)이고 신고로 숨기지 않은 글({@code PostRepositoryQDSL#alive}·{@code #notHidden} 과 같은 뜻).</li>
 * </ul>
 *
 * <p><b>색인</b>: V68 의 pg_trgm GIN — {@code lower(title)} · {@code lower(coalesce(content_text, content))} · {@code lower(guest_name)}(비회원 글) ·
 * profiles {@code lower(nickname)}. 질의의 식이 색인의 식과 글자 그대로 같아야 한다(바꾸면 V 마이그레이션으로 색인도 바꾼다). 3글자 미만 단어는 트라이그램이 없어 색인을 못 타고
 * 최신 글부터 훑으므로 시간 제한(서비스)이 있다. 정렬은 id(= 작성 시각) — 글 표의 id 월 범위 파티션과 같은 순서라 {@code LIMIT} 에 닿으면 멈춘다.
 *
 * <p>Native SQL 인 이유: ① 시간 제한 {@code set_config('statement_timeout', …, true)}(트랜잭션 안에서만) ② 본문 일치 자리({@code strpos})로 조각을 잘라 오는 것
 * ③ 단어 수만큼 늘어나는 조건과 하위 질의 {@code LIMIT} 의 개수 세기 — QueryDSL-JPA 로는 색인 식을 그대로 맞추기 어렵다.
 */
@Repository
public class PostSearchQueries {

    /** 범위 */
    public static final String ALL = "all";
    public static final String TITLE = "title";
    public static final String BODY = "body";
    public static final String AUTHOR = "author";

    /** 조각을 자를 때 일치 자리 앞에 두는 글자 수 */
    public static final int SNIPPET_BEFORE = 40;
    /** 잘라 오는 글자 수(일치 자리 앞 {@value #SNIPPET_BEFORE}자 포함) */
    public static final int SNIPPET_LENGTH = 120;

    /**
     * 찾는 조건.
     *
     * @param tokens 단어(다듬은 것, 1~5개)
     * @param cursor 이 ID 의 다음부터(최신순이면 더 오래된 글, 오래된순이면 더 새 글) — null 이면 처음부터
     */
    public record Criteria(List<Long> boardIds, List<String> tokens, String scope, boolean oldest, Long cursor, Instant now) {
    }

    /**
     * 찾은 글 하나.
     *
     * @param window     본문에서 잘라 온 글자(본문이 걸리지 않았거나 조각을 내지 않는 범위면 null)
     * @param start      잘라 온 글자의 시작 위치(1부터)
     * @param bodyLength 본문 평문의 글자 수
     */
    public record Hit(long id, String window, int start, int bodyLength) {
    }

    private final EntityManager entityManager;

    public PostSearchQueries(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** 이 트랜잭션의 다음 문장부터 한 문장의 시간 제한(밀리초). 넘으면 DB 가 문장을 취소한다(SQLSTATE 57014) */
    public void limitTime(long millis) {
        entityManager.createNativeQuery("SELECT set_config('statement_timeout', CAST(:ms AS text), true)")
                .setParameter("ms", Long.toString(Math.max(1, millis)))
                .getSingleResult();
    }

    /** 결과 수 — {@code cap} 까지만 센다(쪽 번호는 최대 10쪽이라 그 너머는 세지 않는다) */
    public long countUpTo(Criteria c, int cap) {
        Query q = entityManager.createNativeQuery("SELECT count(*) FROM (SELECT 1 FROM posts p WHERE " + where(c) + " LIMIT :cap) t");
        bind(q, c).setParameter("cap", cap);
        Object n = q.getSingleResult();
        return n == null ? 0 : ((Number) n).longValue();
    }

    /**
     * 한 쪽 — 정렬 순서대로.
     *
     * @param snippet 본문 조각을 잘라 올까(범위 all·body)
     */
    public List<Hit> find(Criteria c, long offset, int limit, boolean snippet) {
        String inner = "SELECT p.id FROM posts p WHERE " + where(c) + " ORDER BY p.id " + (c.oldest() ? "ASC" : "DESC") + " LIMIT :limit OFFSET :offset";
        String sql;
        if (snippet) {
            // 찾은 쪽의 글만 다시 읽어 본문 일치 자리를 잰다(본문이 긴 글을 거르는 단계에서 자르지 않게). 자리는 단어 순서로 처음 걸린 단어
            StringBuilder pos = new StringBuilder("coalesce(");
            for (int i = 0; i < c.tokens().size(); i++) {
                pos.append(i == 0 ? "" : ", ").append("nullif(strpos(lower(b.body), lower(CAST(:w").append(i).append(" AS text))), 0)");
            }
            pos.append(", 0)");
            sql = "SELECT h.id, CASE WHEN s.pos > 0 THEN substr(b.body, greatest(s.pos - " + SNIPPET_BEFORE + ", 1), " + SNIPPET_LENGTH + ") END,"
                    + " CASE WHEN s.pos > 0 THEN greatest(s.pos - " + SNIPPET_BEFORE + ", 1) ELSE 0 END, char_length(b.body)"
                    + " FROM (" + inner + ") h"
                    + " JOIN posts p2 ON p2.id = h.id"
                    + " CROSS JOIN LATERAL (SELECT coalesce(p2.content_text, p2.content) AS body) b"
                    + " CROSS JOIN LATERAL (SELECT " + pos + " AS pos) s"
                    + " ORDER BY h.id " + (c.oldest() ? "ASC" : "DESC");
        } else {
            sql = "SELECT h.id, CAST(NULL AS text), 0, 0 FROM (" + inner + ") h ORDER BY h.id " + (c.oldest() ? "ASC" : "DESC");
        }
        Query q = entityManager.createNativeQuery(sql);
        bind(q, c).setParameter("limit", limit).setParameter("offset", offset);
        if (snippet) {
            for (int i = 0; i < c.tokens().size(); i++) {
                q.setParameter("w" + i, c.tokens().get(i));
            }
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        List<Hit> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(new Hit(((Number) r[0]).longValue(), (String) r[1], r[2] == null ? 0 : ((Number) r[2]).intValue(), r[3] == null ? 0 : ((Number) r[3]).intValue()));
        }
        return out;
    }

    /** 공통 조건 — 게시판 · 보이는 글 · 숨김 아님 · 커서 · 단어마다(AND) 범위의 칸 */
    private static String where(Criteria c) {
        StringBuilder sql = new StringBuilder("""
                p.board_id IN (:boards) AND p.status = 'PUBLISHED' AND p.deleted_at IS NULL \
                AND (p.delete_scheduled_at IS NULL OR p.delete_scheduled_at > :now) \
                AND (p.hidden_at IS NULL OR (p.hidden_until IS NOT NULL AND p.hidden_until <= :now))""");
        if (c.cursor() != null) {
            sql.append(c.oldest() ? " AND p.id > :cursor" : " AND p.id < :cursor");
        }
        for (int i = 0; i < c.tokens().size(); i++) {
            String t = "lower(CAST(:t" + i + " AS text))";
            String title = "lower(p.title) LIKE " + t;
            String body = "lower(coalesce(p.content_text, p.content)) LIKE " + t;
            String author = "(p.author_id IN (SELECT pr.user_id FROM profiles pr WHERE lower(pr.nickname) LIKE " + t + ")"
                    + " OR (p.author_id IS NULL AND lower(p.guest_name) LIKE " + t + "))";
            String cond = switch (c.scope()) {
                case TITLE -> title;
                case BODY -> body;
                case AUTHOR -> author;
                default -> title + " OR " + body + " OR " + author;
            };
            sql.append(" AND (").append(cond).append(')');
        }
        return sql.toString();
    }

    private static Query bind(Query q, Criteria c) {
        q.setParameter("boards", c.boardIds()).setParameter("now", c.now());
        if (c.cursor() != null) {
            q.setParameter("cursor", c.cursor());
        }
        for (int i = 0; i < c.tokens().size(); i++) {
            q.setParameter("t" + i, "%" + escapeLike(c.tokens().get(i)) + "%");
        }
        return q;
    }

    /** LIKE 의 특수 글자({@code \ % _})를 글자 그대로 찾게 가린다 — PostgreSQL LIKE 의 기본 탈출 문자는 백슬래시다 */
    static String escapeLike(String token) {
        return token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
