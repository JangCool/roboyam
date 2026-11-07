package com.community.post.repository.master;

import com.community.comment.entity.QCommentDislikeEntity;
import com.community.comment.entity.QCommentEntity;
import com.community.comment.entity.QCommentLikeEntity;
import com.community.post.entity.QPostEntity;
import com.community.post.entity.QPostLikeEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

/**
 * 글·댓글의 숫자 칸(조회·추천·보이는 댓글 수)과 추천·조회 기록 표({@code post_likes}·{@code comment_likes}·{@code comment_dislikes}·{@code post_views}).
 * 트랜잭션은 부르는 서비스의 것을 따른다.
 *
 * <p>숫자 칸은 여러 요청이 동시에 올리므로 읽고-고쳐-쓰기가 아니라 원자적 대량 UPDATE({@code set(n, n + 1)})로 고치고, 같은 트랜잭션에서 새 값을 다시 읽는다 —
 * UPDATE 가 잡은 행 잠금이 커밋까지 남으므로 그 사이에 다른 요청이 값을 바꾸지 못한다(예전 {@code UPDATE … RETURNING} 과 같은 값). 엔티티의 숫자 칸은
 * {@code updatable = false} 라 1차 캐시의 글·댓글이 덮어쓰지 않는다.
 *
 * <p>Native SQL 로 남긴 것: 추천·조회 기록 넣기의 {@code INSERT … ON CONFLICT}(「처음이면 넣고, 이미 있으면 안 넣는다/시간이 지났으면 고친다」를 한 문장으로 판정 —
 * 두 문장으로 나누면 동시 요청에서 {@code duplicate key} 로 트랜잭션이 깨진다)와 작성자 줄 세우기({@code pg_advisory_xact_lock}).
 */
@Repository
public class PostCounterQueries extends RoboyamRepositoryQDSL {

    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final QCommentEntity COMMENT = QCommentEntity.commentEntity;
    private static final QPostLikeEntity POST_LIKE = QPostLikeEntity.postLikeEntity;
    private static final QCommentLikeEntity COMMENT_LIKE = QCommentLikeEntity.commentLikeEntity;
    private static final QCommentDislikeEntity COMMENT_DISLIKE = QCommentDislikeEntity.commentDislikeEntity;

    private final EntityManager entityManager;

    public PostCounterQueries(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    /** 글 추천을 넣는다 — 처음이면 true, 이미 추천했으면 false */
    public boolean insertPostLike(long postId, long userId) {
        return entityManager.createNativeQuery("INSERT INTO post_likes (post_id, user_id) VALUES (?1, ?2) ON CONFLICT DO NOTHING").setParameter(1, postId)
                .setParameter(2, userId).executeUpdate() == 1;
    }

    public boolean postLiked(long postId, long userId) {
        return query.selectOne().from(POST_LIKE).where(POST_LIKE.postId.eq(postId), POST_LIKE.userId.eq(userId)).fetchFirst() != null;
    }

    /** 글 추천 수 +1 → 새 값 */
    public int incrementPostLikes(long postId) {
        query.update(POST).set(POST.likeCount, POST.likeCount.add(1)).where(POST.id.eq(postId)).execute();
        return orZero(query.select(POST.likeCount).from(POST).where(POST.id.eq(postId)).fetchOne());
    }

    /** 댓글 추천을 넣는다 — 처음이면 true */
    public boolean insertCommentLike(long commentId, long postId, long userId) {
        return entityManager.createNativeQuery("INSERT INTO comment_likes (comment_id, user_id, post_id) VALUES (?1, ?2, ?3) ON CONFLICT DO NOTHING")
                .setParameter(1, commentId).setParameter(2, userId).setParameter(3, postId).executeUpdate() == 1;
    }

    /** 댓글 추천 수 +1 → 새 값 */
    public int incrementCommentLikes(long postId, long commentId) {
        query.update(COMMENT).set(COMMENT.likeCount, COMMENT.likeCount.add(1)).where(COMMENT.postId.eq(postId), COMMENT.id.eq(commentId)).execute();
        return commentLikes(postId, commentId);
    }

    /** 댓글 추천을 푼다(비추천을 누르면 — 추천과 비추천은 둘 중 하나만) — 있었으면 true */
    public boolean deleteCommentLike(long commentId, long userId) {
        return query.delete(COMMENT_LIKE).where(COMMENT_LIKE.commentId.eq(commentId), COMMENT_LIKE.userId.eq(userId)).execute() == 1;
    }

    /** 댓글 추천 수 -1(0 아래로 내려가지 않는다) → 새 값 */
    public int decrementCommentLikes(long postId, long commentId) {
        query.update(COMMENT).set(COMMENT.likeCount, atLeastZero(COMMENT.likeCount.subtract(1))).where(COMMENT.postId.eq(postId), COMMENT.id.eq(commentId)).execute();
        return commentLikes(postId, commentId);
    }

    /** 댓글 비추천을 넣는다(V78 {@code comment_dislikes}) — 처음이면 true */
    public boolean insertCommentDislike(long commentId, long postId, long userId) {
        return entityManager.createNativeQuery("INSERT INTO comment_dislikes (comment_id, user_id, post_id) VALUES (?1, ?2, ?3) ON CONFLICT DO NOTHING")
                .setParameter(1, commentId).setParameter(2, userId).setParameter(3, postId).executeUpdate() == 1;
    }

    /** 댓글 비추천을 푼다 — 있었으면 true */
    public boolean deleteCommentDislike(long commentId, long userId) {
        return query.delete(COMMENT_DISLIKE).where(COMMENT_DISLIKE.commentId.eq(commentId), COMMENT_DISLIKE.userId.eq(userId)).execute() == 1;
    }

    /** 댓글 비추천 수를 {@code delta} 만큼(0 아래로 내려가지 않는다) → 새 값 */
    public int addCommentDislikes(long postId, long commentId, int delta) {
        query.update(COMMENT).set(COMMENT.dislikeCount, atLeastZero(COMMENT.dislikeCount.add(delta))).where(COMMENT.postId.eq(postId), COMMENT.id.eq(commentId))
                .execute();
        return orZero(query.select(COMMENT.dislikeCount).from(COMMENT).where(COMMENT.postId.eq(postId), COMMENT.id.eq(commentId)).fetchOne());
    }

    /** 지금 댓글의 추천·비추천 수 — {@code [like, dislike]}(댓글이 없으면 {@link EmptyResultDataAccessException}) */
    public int[] commentReactionCounts(long postId, long commentId) {
        Tuple t = query.select(COMMENT.likeCount, COMMENT.dislikeCount).from(COMMENT).where(COMMENT.postId.eq(postId), COMMENT.id.eq(commentId)).fetchOne();
        if (t == null) {
            throw new EmptyResultDataAccessException(1);
        }
        return new int[] {orZero(t.get(COMMENT.likeCount)), orZero(t.get(COMMENT.dislikeCount))};
    }

    /** 이 회원이 이 댓글을 추천했는가 */
    public boolean commentLiked(long commentId, long userId) {
        return query.selectOne().from(COMMENT_LIKE).where(COMMENT_LIKE.commentId.eq(commentId), COMMENT_LIKE.userId.eq(userId)).fetchFirst() != null;
    }

    /** 이 회원이 이 댓글을 비추천했는가 */
    public boolean commentDisliked(long commentId, long userId) {
        return query.selectOne().from(COMMENT_DISLIKE).where(COMMENT_DISLIKE.commentId.eq(commentId), COMMENT_DISLIKE.userId.eq(userId)).fetchFirst() != null;
    }

    /** 이 글에서 이 회원이 비추천한 댓글 ID */
    public Set<Long> dislikedComments(long postId, long userId) {
        return new HashSet<>(query.select(COMMENT_DISLIKE.commentId).from(COMMENT_DISLIKE).where(COMMENT_DISLIKE.postId.eq(postId), COMMENT_DISLIKE.userId.eq(userId))
                .fetch());
    }

    /** 이 글에서 이 회원이 추천한 댓글 ID */
    public Set<Long> likedComments(long postId, long userId) {
        return new HashSet<>(query.select(COMMENT_LIKE.commentId).from(COMMENT_LIKE).where(COMMENT_LIKE.postId.eq(postId), COMMENT_LIKE.userId.eq(userId)).fetch());
    }

    /**
     * 조회 기록 — 이 사람이 처음 보거나 마지막으로 센 때가 {@code countedBefore} 보다 앞이면 기록을 새로 하고 true(조회수를 올린다), 아니면 false.
     *
     * @param viewerKey 회원 {@code m:<id>} · 비회원 {@code g:<IP 의 SHA-256>}
     */
    public boolean recordView(long postId, String viewerKey, Instant countedBefore) {
        return entityManager.createNativeQuery("INSERT INTO post_views (post_id, viewer_key, viewed_at) VALUES (?1, ?2, now()) "
                        + "ON CONFLICT (post_id, viewer_key) DO UPDATE SET viewed_at = now() WHERE post_views.viewed_at < ?3")
                .setParameter(1, postId).setParameter(2, viewerKey).setParameter(3, countedBefore).executeUpdate() == 1;
    }

    /** 조회수 +1 → 새 값 */
    public int incrementViews(long postId) {
        query.update(POST).set(POST.viewCount, POST.viewCount.add(1)).where(POST.id.eq(postId)).execute();
        return orZero(query.select(POST.viewCount).from(POST).where(POST.id.eq(postId)).fetchOne());
    }

    /** 보이는 댓글 수를 {@code delta} 만큼 바꾼다(0 아래로 내려가지 않는다) → 새 값 */
    public int addCommentCount(long postId, int delta) {
        query.update(POST).set(POST.commentCount, atLeastZero(POST.commentCount.add(delta))).where(POST.id.eq(postId)).execute();
        return orZero(query.select(POST.commentCount).from(POST).where(POST.id.eq(postId)).fetchOne());
    }

    /**
     * 작성자 단위로 쓰기를 줄 세운다(트랜잭션이 끝나면 풀린다) — 작성 간격·하루 한도·같은 글 반복을 확인하고 넣는 사이에 같은 사람의 다른 요청이 끼지 못하게.
     *
     * <p>Native SQL 인 이유: {@code pg_advisory_xact_lock} 은 PostgreSQL 함수다(잠글 행이 아직 없다).
     *
     * @param key 예: {@code post:m:<회원ID>} · {@code comment:g:<IP>}
     */
    public void lockWriter(String key) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(7340101, hashtext(?1))::text").setParameter(1, key).getSingleResult();
    }

    private int commentLikes(long postId, long commentId) {
        return orZero(query.select(COMMENT.likeCount).from(COMMENT).where(COMMENT.postId.eq(postId), COMMENT.id.eq(commentId)).fetchOne());
    }

    private static NumberExpression<Integer> atLeastZero(NumberExpression<Integer> value) {
        return Expressions.numberTemplate(Integer.class, "greatest({0}, 0)", value);
    }

    private static int orZero(Integer n) {
        return n == null ? 0 : n;
    }
}
