package com.community.post.repository.master;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 글·댓글의 숫자 칸(조회·추천·보이는 댓글 수)과 추천·조회 기록 표.
 *
 * <p>native SQL 인 이유: ① 숫자 칸은 여러 요청이 동시에 올리므로 읽고-고쳐-쓰기가 아니라 {@code UPDATE … SET n = n + 1 RETURNING n} 한 문장이어야 한다
 * ② 추천·조회 기록은 {@code INSERT … ON CONFLICT} (PostgreSQL 전용)로 「처음이면 넣고, 이미 있으면 안 넣는다/시간이 지났으면 고친다」를 한 번에 판정한다
 * — JPQL·QueryDSL 에 없다. 트랜잭션은 부르는 서비스의 것을 따른다(같은 DataSource).
 */
@Repository
public class PostCounterQueries {

    private final JdbcTemplate jdbc;

    public PostCounterQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 글 추천을 넣는다 — 처음이면 true, 이미 추천했으면 false */
    public boolean insertPostLike(long postId, long userId) {
        return jdbc.update("INSERT INTO post_likes (post_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING", postId, userId) == 1;
    }

    public boolean postLiked(long postId, long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM post_likes WHERE post_id = ? AND user_id = ?)", Boolean.class, postId, userId));
    }

    /** 글 추천 수 +1 → 새 값 */
    public int incrementPostLikes(long postId) {
        Integer n = jdbc.queryForObject("UPDATE posts SET like_count = like_count + 1 WHERE id = ? RETURNING like_count", Integer.class, postId);
        return n == null ? 0 : n;
    }

    /** 댓글 추천을 넣는다 — 처음이면 true */
    public boolean insertCommentLike(long commentId, long postId, long userId) {
        return jdbc.update("INSERT INTO comment_likes (comment_id, user_id, post_id) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", commentId, userId, postId) == 1;
    }

    /** 댓글 추천 수 +1 → 새 값 */
    public int incrementCommentLikes(long postId, long commentId) {
        Integer n = jdbc.queryForObject("UPDATE comments SET like_count = like_count + 1 WHERE post_id = ? AND id = ? RETURNING like_count", Integer.class,
                postId, commentId);
        return n == null ? 0 : n;
    }

    /** 댓글 추천을 푼다(비추천을 누르면 — 추천과 비추천은 둘 중 하나만) — 있었으면 true */
    public boolean deleteCommentLike(long commentId, long userId) {
        return jdbc.update("DELETE FROM comment_likes WHERE comment_id = ? AND user_id = ?", commentId, userId) == 1;
    }

    /** 댓글 추천 수 -1(0 아래로 내려가지 않는다) → 새 값 */
    public int decrementCommentLikes(long postId, long commentId) {
        Integer n = jdbc.queryForObject("UPDATE comments SET like_count = greatest(like_count - 1, 0) WHERE post_id = ? AND id = ? RETURNING like_count",
                Integer.class, postId, commentId);
        return n == null ? 0 : n;
    }

    /** 댓글 비추천을 넣는다(V78 {@code comment_dislikes}) — 처음이면 true */
    public boolean insertCommentDislike(long commentId, long postId, long userId) {
        return jdbc.update("INSERT INTO comment_dislikes (comment_id, user_id, post_id) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", commentId, userId, postId) == 1;
    }

    /** 댓글 비추천을 푼다 — 있었으면 true */
    public boolean deleteCommentDislike(long commentId, long userId) {
        return jdbc.update("DELETE FROM comment_dislikes WHERE comment_id = ? AND user_id = ?", commentId, userId) == 1;
    }

    /** 댓글 비추천 수를 {@code delta} 만큼(0 아래로 내려가지 않는다) → 새 값 */
    public int addCommentDislikes(long postId, long commentId, int delta) {
        Integer n = jdbc.queryForObject("UPDATE comments SET dislike_count = greatest(dislike_count + ?, 0) WHERE post_id = ? AND id = ? RETURNING dislike_count",
                Integer.class, delta, postId, commentId);
        return n == null ? 0 : n;
    }

    /** 지금 댓글의 추천·비추천 수 — {@code [like, dislike]} */
    public int[] commentReactionCounts(long postId, long commentId) {
        return jdbc.queryForObject("SELECT like_count, dislike_count FROM comments WHERE post_id = ? AND id = ?",
                (rs, i) -> new int[]{rs.getInt(1), rs.getInt(2)}, postId, commentId);
    }

    /** 이 회원이 이 댓글을 추천했는가 */
    public boolean commentLiked(long commentId, long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM comment_likes WHERE comment_id = ? AND user_id = ?)", Boolean.class,
                commentId, userId));
    }

    /** 이 회원이 이 댓글을 비추천했는가 */
    public boolean commentDisliked(long commentId, long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM comment_dislikes WHERE comment_id = ? AND user_id = ?)", Boolean.class,
                commentId, userId));
    }

    /** 이 글에서 이 회원이 비추천한 댓글 ID */
    public Set<Long> dislikedComments(long postId, long userId) {
        return new HashSet<>(jdbc.queryForList("SELECT comment_id FROM comment_dislikes WHERE post_id = ? AND user_id = ?", Long.class, postId, userId));
    }

    /** 이 글에서 이 회원이 추천한 댓글 ID */
    public Set<Long> likedComments(long postId, long userId) {
        List<Long> ids = jdbc.queryForList("SELECT comment_id FROM comment_likes WHERE post_id = ? AND user_id = ?", Long.class, postId, userId);
        return new HashSet<>(ids);
    }

    /**
     * 조회 기록 — 이 사람이 처음 보거나 마지막으로 센 때가 {@code countedBefore} 보다 앞이면 기록을 새로 하고 true(조회수를 올린다), 아니면 false.
     *
     * @param viewerKey 회원 {@code m:<id>} · 비회원 {@code g:<IP 의 SHA-256>}
     */
    public boolean recordView(long postId, String viewerKey, Instant countedBefore) {
        return jdbc.update("INSERT INTO post_views (post_id, viewer_key, viewed_at) VALUES (?, ?, now()) "
                + "ON CONFLICT (post_id, viewer_key) DO UPDATE SET viewed_at = now() WHERE post_views.viewed_at < ?",
                postId, viewerKey, Timestamp.from(countedBefore)) == 1;
    }

    /** 조회수 +1 → 새 값 */
    public int incrementViews(long postId) {
        Integer n = jdbc.queryForObject("UPDATE posts SET view_count = view_count + 1 WHERE id = ? RETURNING view_count", Integer.class, postId);
        return n == null ? 0 : n;
    }

    /** 보이는 댓글 수를 {@code delta} 만큼 바꾼다(0 아래로 내려가지 않는다) → 새 값 */
    public int addCommentCount(long postId, int delta) {
        Integer n = jdbc.queryForObject("UPDATE posts SET comment_count = greatest(comment_count + ?, 0) WHERE id = ? RETURNING comment_count", Integer.class,
                delta, postId);
        return n == null ? 0 : n;
    }

    /**
     * 작성자 단위로 쓰기를 줄 세운다(트랜잭션이 끝나면 풀린다) — 작성 간격·하루 한도·같은 글 반복을 확인하고 넣는 사이에 같은 사람의 다른 요청이 끼지 못하게.
     *
     * @param key 예: {@code post:m:<회원ID>} · {@code comment:g:<IP>}
     */
    public void lockWriter(String key) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(7340101, hashtext(?))::text", String.class, key);
    }
}
