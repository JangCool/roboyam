package com.community.comment.repository.master;

import com.community.comment.entity.QCommentEntity;
import com.community.post.dto.WriteRateData;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 댓글 질의 — 댓글 ID 만으로 글 찾기(주소에 글 ID 가 없는 API), 작성 속도 */
@Repository
public class CommentRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QCommentEntity COMMENT = QCommentEntity.commentEntity;

    public CommentRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 댓글이 달린 글의 ID — 해시 조각마다 {@code ix_comments_id} 를 한 번씩 찌른다(V46) */
    public Optional<Long> postIdOf(long commentId) {
        return Optional.ofNullable(query.select(COMMENT.postId).from(COMMENT).where(COMMENT.id.eq(commentId)).fetchFirst());
    }

    /**
     * {@code since} 뒤에 같은 작성자(회원 ID, 비회원은 작성 IP)가 쓴 댓글 수와 마지막 시각(지운 것 포함). 댓글 ID 도 Snowflake 라 id 범위로 색인을 좁힌다.
     */
    public WriteRateData rate(Long authorId, String ip, Instant since, long sinceId) {
        BooleanExpression author = authorId != null ? COMMENT.authorId.eq(authorId) : COMMENT.authorId.isNull().and(COMMENT.authorIp.eq(ip));
        Tuple t = query.select(COMMENT.id.count(), COMMENT.createdAt.max()).from(COMMENT)
                .where(author, COMMENT.id.goe(sinceId), COMMENT.createdAt.goe(since))
                .fetchOne();
        long count = t == null || t.get(0, Long.class) == null ? 0 : t.get(0, Long.class);
        return new WriteRateData(count, t == null ? null : t.get(1, Instant.class));
    }
}
