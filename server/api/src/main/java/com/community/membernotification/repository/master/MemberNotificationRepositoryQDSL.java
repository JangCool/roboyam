package com.community.membernotification.repository.master;

import com.community.comment.entity.CommentEntity;
import com.community.comment.entity.QCommentEntity;
import com.community.membernotification.entity.MemberNotificationEntity;
import com.community.membernotification.entity.QMemberNotificationEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 회원 알림 조회·읽음 처리 — <b>본인 것만</b>(모든 질의에 {@code user_id} 조건이 있다). {@code types} 가 null 이면 모든 종류, 비어 있으면 아무것도 맞지 않는다.
 * 트랜잭션은 부르는 서비스가 연다.
 */
@Repository
public class MemberNotificationRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QMemberNotificationEntity N = QMemberNotificationEntity.memberNotificationEntity;
    private static final QCommentEntity COMMENT = QCommentEntity.commentEntity;

    public MemberNotificationRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 최신순 한 쪽 — {@code beforeId} 보다 오래된 것부터 {@code limit} 개 */
    public List<MemberNotificationEntity> page(long userId, Collection<String> types, Long beforeId, int limit) {
        if (types != null && types.isEmpty()) {
            return List.of();
        }
        return query.selectFrom(N)
                .where(N.userId.eq(userId), types == null ? null : N.type.in(types), beforeId == null ? null : N.id.lt(beforeId))
                .orderBy(N.id.desc())
                .limit(limit)
                .fetch();
    }

    /** 안 읽은 수 — {@code cap} 개까지만 센다(색인 {@code ix_member_notifications_unread} 를 cap 개만 훑는다) */
    public int unread(long userId, int cap) {
        return query.select(N.id).from(N).where(N.userId.eq(userId), N.readAt.isNull()).limit(cap).fetch().size();
    }

    /** 내 알림인가 */
    public boolean exists(long userId, long id) {
        return query.selectOne().from(N).where(N.id.eq(id), N.userId.eq(userId)).fetchFirst() != null;
    }

    /** 읽음 처리 — 이미 읽었으면 그대로(처음 읽은 시각을 지킨다) */
    public long markRead(long userId, long id, Instant now) {
        return query.update(N).set(N.readAt, now).where(N.id.eq(id), N.userId.eq(userId), N.readAt.isNull()).execute();
    }

    /** 안 읽은 알림(그 종류만, null 이면 모두)을 읽음 처리한다. 바뀐 줄 수 */
    public long markAllRead(long userId, Collection<String> types, Instant now) {
        if (types != null && types.isEmpty()) {
            return 0;
        }
        return query.update(N).set(N.readAt, now).where(N.userId.eq(userId), N.readAt.isNull(), types == null ? null : N.type.in(types)).execute();
    }

    /** 알림이 가리키는 댓글들 — 글 ID 로 파티션을 좁히고 댓글 ID 로 고른다 */
    public List<CommentEntity> comments(Collection<Long> postIds, Collection<Long> commentIds) {
        if (postIds.isEmpty() || commentIds.isEmpty()) {
            return List.of();
        }
        BooleanExpression where = COMMENT.postId.in(postIds).and(COMMENT.id.in(commentIds));
        return query.selectFrom(COMMENT).where(where).fetch();
    }
}
