package com.community.member.repository.master;

import com.community.zconfiguration.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.springframework.stereotype.Repository;

/**
 * 회원의 활동 수 집계 — 관리자 회원 상세의 활동 요약과 글·댓글 작성 직후의 즉시 승급({@code GradePromotionService#promoteOnActivity})이 <b>같은 정의</b>를 쓴다.
 * <b>트랜잭션은 부르는 서비스가 연다.</b>
 *
 * <p><b>센다</b> — 공개 게시판에 지금 보이는 회원 작성분만(비회원 글·댓글은 어느 회원에게도 세지 않는다):
 * <ul>
 *   <li>글: {@code status = 'PUBLISHED'}(임시저장 제외) · 지우지 않음({@code deleted_at IS NULL}) · 예약 삭제 시각 전({@code delete_scheduled_at IS NULL OR > now})
 *       · 지금 숨김 아님({@code hidden_at IS NULL OR (hidden_until IS NOT NULL AND hidden_until <= now)} — 임시조치가 끝난 글은 다시 센다).
 *       {@code PostEntity#isGoneAt}·{@code #isHiddenAt} 의 부정, {@code PostRepositoryQDSL#alive}·{@code #notHidden} 과 같은 뜻이다.</li>
 *   <li>댓글: 지우지 않음 · 지금 숨김 아님(같은 숨김 규칙, 댓글 칸 기준 — {@code CommentEntity#isDeleted}·{@code #isHiddenAt}). 댓글에는 예약 삭제가 없다.
 *       그 댓글이 달린 글의 삭제·숨김은 보지 않는다(글과 조인하지 않는다).</li>
 * </ul>
 * 한쪽 규칙을 고치면 엔티티 판정·공개 게시판 질의·이 집계를 함께 고친다.
 *
 * <p><b>색인</b>: 작성자 색인 {@code ix_posts_author_recent}·{@code ix_comments_author_recent}({@code (author_id, id DESC) WHERE author_id IS NOT NULL}, V46)을 탄다.
 * 보임 판정은 지금 시각과 비교하므로 부분 색인 조건에 넣을 수 없고 행을 읽어 거른다(작성자 행 수에 비례). 최근 구간은 id 하한({@link SnowflakeIdGenerator#floorIdAt})과
 * {@code created_at} 을 함께 건다 — 글·댓글은 만들 때의 시각으로 id 를 받아 둘이 어긋나지 않는다.
 *
 * <p>Native SQL 인 이유: ① 요약은 글·댓글 각각 한 번 읽으며 전체 수·최근 수·추천 합을 {@code FILTER} 집계로 함께 내고 두 표를 한 문장(왕복 한 번)으로 묶는다.
 * ② 상한 집계는 {@code UNION ALL} 과 하위 질의의 {@code LIMIT} 이 필요하다 — 둘 다 JPQL/QueryDSL-JPA 로 쓸 수 없다.
 */
@Repository
public class MemberActivityRepository {

    /** 활동 요약 — 글·댓글 전체 수, {@code since} 이후에 쓴 수, 받은 추천 합(보이는 글·댓글의 {@code like_count} 합) */
    public record Counts(long posts, long comments, long recentPosts, long recentComments, long likesReceived) {
    }

    /** 보이는 글 — 클래스 설명 */
    private static final String POST_VISIBLE = """
            author_id = :userId AND status = 'PUBLISHED' AND deleted_at IS NULL
            AND (delete_scheduled_at IS NULL OR delete_scheduled_at > :now)
            AND (hidden_at IS NULL OR (hidden_until IS NOT NULL AND hidden_until <= :now))""";

    /** 보이는 댓글 — 클래스 설명 */
    private static final String COMMENT_VISIBLE = """
            author_id = :userId AND deleted_at IS NULL
            AND (hidden_at IS NULL OR (hidden_until IS NOT NULL AND hidden_until <= :now))""";

    private final EntityManager entityManager;

    public MemberActivityRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** 회원의 활동 요약. {@code since}(포함) 이후에 쓴 것이 최근 수다. 보임 판정의 기준 시각은 {@code now} */
    public Counts countOf(long userId, Instant since, Instant now) {
        Object[] row = (Object[]) entityManager.createNativeQuery("""
                        SELECT p.n, p.recent, p.likes, c.n, c.recent, c.likes
                          FROM (SELECT count(*) AS n,
                                       count(*) FILTER (WHERE id >= :floorId AND created_at >= :since) AS recent,
                                       coalesce(sum(like_count), 0) AS likes
                                  FROM posts WHERE %s) p,
                               (SELECT count(*) AS n,
                                       count(*) FILTER (WHERE id >= :floorId AND created_at >= :since) AS recent,
                                       coalesce(sum(like_count), 0) AS likes
                                  FROM comments WHERE %s) c
                        """.formatted(POST_VISIBLE, COMMENT_VISIBLE))
                .setParameter("userId", userId).setParameter("now", now).setParameter("since", since)
                .setParameter("floorId", SnowflakeIdGenerator.floorIdAt(since))
                .getSingleResult();
        return new Counts(num(row[0]), num(row[3]), num(row[1]), num(row[4]), num(row[2]) + num(row[5]));
    }

    /**
     * 보이는 글+댓글 수를 <b>{@code limit} 까지만</b> 센다 — 즉시 승급이 「다음 등급에 필요한 활동 수를 채웠는가」만 알면 되므로 매 글쓰기마다 전체를 세지 않는다.
     * 글을 먼저 읽고 모자란 만큼만 댓글을 읽는다(바깥 {@code LIMIT} 에 닿으면 멈춘다).
     *
     * @return {@code min(실제 수, limit)}. {@code limit <= 0} 이면 0(질의하지 않는다)
     */
    public long countUpTo(long userId, long limit, Instant now) {
        if (limit <= 0) {
            return 0;
        }
        Object n = entityManager.createNativeQuery("""
                        SELECT count(*) FROM (
                            SELECT 1 FROM (
                                (SELECT 1 FROM posts WHERE %s LIMIT :limit)
                                UNION ALL
                                (SELECT 1 FROM comments WHERE %s LIMIT :limit)
                            ) u LIMIT :limit
                        ) t
                        """.formatted(POST_VISIBLE, COMMENT_VISIBLE))
                .setParameter("userId", userId).setParameter("now", now).setParameter("limit", limit)
                .getSingleResult();
        return num(n);
    }

    private static long num(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
