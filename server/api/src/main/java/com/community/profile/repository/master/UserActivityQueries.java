package com.community.profile.repository.master;

import com.community.comment.entity.QCommentEntity;
import com.community.post.dto.PostRowData;
import com.community.post.entity.ContentFormat;
import com.community.post.entity.QPostEntity;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 공개 프로필의 활동 질의 — 수치·작성글·작성 댓글. docs/features/member-activity-contract.md 「공개 프로필」.
 *
 * <p><b>볼 수 있는 글</b>: 공개 목록에 보이는 글({@link PostRepositoryQDSL#alive}) · 지금 숨김이 아님({@link PostRepositoryQDSL#notHidden}) · 켜진 게시판({@code boardIds}).
 * 게시판 목록과 달리 숨긴 글은 자리도 남기지 않고 뺀다. 소모임 글은 비멤버에게 보이는 것만(소모임 6단계). <b>볼 수 있는 댓글</b>: 지우지 않음 · 지금 숨김 아님 · 볼 수 있는 <b>일반</b> 글에 달림.
 * 작성자 색인 {@code ix_posts_author_recent}·{@code ix_comments_author_recent}({@code (author_id, id DESC)}, V46)을 탄다.
 */
@Repository
public class UserActivityQueries extends RoboyamRepositoryQDSL {

    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final QCommentEntity COMMENT = QCommentEntity.commentEntity;

    public UserActivityQueries(JPAQueryFactory query) {
        super(query);
    }

    /** 수치 — 글 수 · 댓글 수 · 그 글·댓글이 받은 추천 합 */
    public record Stats(long posts, long comments, long likesReceived) {
    }

    /** 작성 댓글 한 줄을 그리는 데 필요한 칸 */
    public record CommentRow(long id, long postId, String postTitle, long boardId, ContentFormat format, String content, int likeCount, boolean reply,
                             Instant createdAt) {
    }

    /** 볼 수 있는 글 조건 — 소모임 글은 비멤버에게 보이는 것만({@link PostRepositoryQDSL#feedVisible}, 소모임 6단계) */
    private static BooleanExpression visiblePost(Collection<Long> boardIds, Instant now) {
        return PostRepositoryQDSL.alive(POST, now).and(PostRepositoryQDSL.notHidden(POST, now)).and(POST.boardId.in(boardIds)).and(PostRepositoryQDSL.feedVisible(POST));
    }

    /**
     * 작성 댓글이 달린 글 조건 — 볼 수 있는 일반 글만. 소모임 글의 댓글은 넣지 않는다: 공개 글이어도 공개 전의 댓글은 비멤버에게 가려지고(MEMBERS_ONLY)
     * 나간 멤버의 댓글은 「탈퇴한 회원」으로 가려지므로(GROUP_LEFT) 프로필에 모으면 가린 것이 드러난다(소모임 6단계 — 로보얌 판단).
     */
    private static BooleanExpression commentablePost(Collection<Long> boardIds, Instant now) {
        return visiblePost(boardIds, now).and(POST.groupId.isNull());
    }

    /** 볼 수 있는 댓글 조건(글 조건은 따로) */
    private static BooleanExpression visibleComment(Instant now) {
        return COMMENT.deletedAt.isNull().and(COMMENT.hiddenAt.isNull().or(COMMENT.hiddenUntil.isNotNull().and(COMMENT.hiddenUntil.loe(now))));
    }

    /** @param boardIds 켜진 게시판 — 비면 모두 0 */
    public Stats stats(long userId, Collection<Long> boardIds, Instant now) {
        if (boardIds.isEmpty()) {
            return new Stats(0, 0, 0);
        }
        Tuple p = query.select(POST.id.count(), likeSum(POST.likeCount)).from(POST)
                .where(POST.authorId.eq(userId), visiblePost(boardIds, now))
                .fetchOne();
        Tuple c = query.select(COMMENT.id.count(), likeSum(COMMENT.likeCount)).from(COMMENT)
                .join(POST).on(POST.id.eq(COMMENT.postId))
                .where(COMMENT.authorId.eq(userId), visibleComment(now), commentablePost(boardIds, now))
                .fetchOne();
        long posts = num(p, 0);
        long comments = num(c, 0);
        return new Stats(posts, comments, num(p, 1) + num(c, 1));
    }

    /**
     * 작성글 한 쪽 — 최신이 위(id 내림차순). {@code limit + 1} 줄까지 읽는다(다음 쪽이 있는지 보려고).
     *
     * @param beforeId 이 id 보다 오래된 글만(커서) — null 이면 처음부터
     */
    public List<PostRowData> posts(long userId, Collection<Long> boardIds, Long beforeId, int limit, Instant now) {
        if (boardIds.isEmpty()) {
            return List.of();
        }
        BooleanExpression where = POST.authorId.eq(userId).and(visiblePost(boardIds, now));
        if (beforeId != null) {
            where = where.and(POST.id.lt(beforeId));
        }
        return query.select(Projections.constructor(PostRowData.class, POST.id, POST.boardId, POST.prefixId, POST.authorId, POST.guestName, POST.authorIp,
                        POST.title, POST.hasImage, POST.adult, POST.viewCount, POST.likeCount, POST.commentCount, POST.pinnedAt, POST.hiddenAt, POST.hiddenUntil,
                        POST.hiddenReason, POST.createdAt, POST.groupId))
                .from(POST).where(where)
                .orderBy(POST.id.desc())
                .limit(limit + 1L)
                .fetch();
    }

    /** 작성 댓글 한 쪽 — 최신이 위(댓글 id 내림차순), {@code limit + 1} 줄까지 */
    public List<CommentRow> comments(long userId, Collection<Long> boardIds, Long beforeId, int limit, Instant now) {
        if (boardIds.isEmpty()) {
            return List.of();
        }
        BooleanExpression where = COMMENT.authorId.eq(userId).and(visibleComment(now)).and(commentablePost(boardIds, now));
        if (beforeId != null) {
            where = where.and(COMMENT.id.lt(beforeId));
        }
        List<Tuple> rows = query.select(COMMENT.id, COMMENT.postId, POST.title, POST.boardId, COMMENT.contentFormat, COMMENT.content, COMMENT.likeCount,
                        COMMENT.parentId, COMMENT.createdAt)
                .from(COMMENT).join(POST).on(POST.id.eq(COMMENT.postId))
                .where(where)
                .orderBy(COMMENT.id.desc())
                .limit(limit + 1L)
                .fetch();
        return rows.stream().map(t -> new CommentRow(t.get(COMMENT.id), t.get(COMMENT.postId), t.get(POST.title), t.get(POST.boardId), t.get(COMMENT.contentFormat),
                t.get(COMMENT.content), t.get(COMMENT.likeCount), t.get(COMMENT.parentId) != null, t.get(COMMENT.createdAt))).toList();
    }

    /** 추천 합 — int 칸의 sum 은 DB·Hibernate 가 bigint(Long)로 돌려준다 */
    private static NumberExpression<Long> likeSum(NumberPath<Integer> likes) {
        return Expressions.numberTemplate(Long.class, "coalesce(sum({0}), 0)", likes);
    }

    private static long num(Tuple t, int index) {
        if (t == null) {
            return 0;
        }
        Long value = t.get(index, Long.class);
        return value == null ? 0 : value;
    }
}
