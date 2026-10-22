package com.community.member.repository.master;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.springframework.stereotype.Repository;

/**
 * 회원의 글·댓글 수 집계(활동 요약). <b>트랜잭션은 부르는 서비스가 연다.</b>
 *
 * <p>Native SQL 인 이유: 글·댓글은 파티션 표이고 아직 엔티티가 없다(쓰기 기능은 후속). 네 개의 수를 스칼라 서브쿼리 한 문장으로 가져와 왕복을 한 번으로 줄인다.
 *
 * <p>집계 기준 — 회원 작성분 중 공개(글은 {@code PUBLISHED}, 임시저장 제외)·삭제 안 됨·숨김 아님. <b>조건은 {@code V45__post_comment_author_indexes.sql} 의 부분 색인 조건과 글자 그대로 같아야 한다</b>
 * (달라지면 색인을 못 타고 파티션 전체를 훑는다) — 한쪽을 고치면 다른 쪽도 고친다. 댓글은 그 글이 삭제·숨김이어도 센다(글과 조인하지 않는다 — 조인하면 색인으로 끝나지 않는다).
 * 전체 수는 작성자 색인의 항목 수이고(색인만 읽는다), 최근 구간은 같은 색인의 {@code created_at} 범위다.
 */
@Repository
public class MemberActivityRepository {

    /** 활동 수 — 글·댓글 전체 수와 {@code since} 이후에 쓴 수 */
    public record Counts(long posts, long comments, long recentPosts, long recentComments) {
    }

    private final EntityManager entityManager;

    public MemberActivityRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** 회원의 글·댓글 수. {@code since}(포함) 이후에 쓴 것이 최근 수다 */
    public Counts countOf(long userId, Instant since) {
        Object[] row = (Object[]) entityManager.createNativeQuery("""
                        SELECT (SELECT count(*) FROM posts
                                 WHERE author_id = :userId AND status = 'PUBLISHED' AND deleted_at IS NULL AND hidden_at IS NULL),
                               (SELECT count(*) FROM comments
                                 WHERE author_id = :userId AND deleted_at IS NULL AND hidden_at IS NULL),
                               (SELECT count(*) FROM posts
                                 WHERE author_id = :userId AND status = 'PUBLISHED' AND deleted_at IS NULL AND hidden_at IS NULL AND created_at >= :since),
                               (SELECT count(*) FROM comments
                                 WHERE author_id = :userId AND deleted_at IS NULL AND hidden_at IS NULL AND created_at >= :since)
                        """)
                .setParameter("userId", userId).setParameter("since", since)
                .getSingleResult();
        return new Counts(((Number) row[0]).longValue(), ((Number) row[1]).longValue(), ((Number) row[2]).longValue(), ((Number) row[3]).longValue());
    }
}
