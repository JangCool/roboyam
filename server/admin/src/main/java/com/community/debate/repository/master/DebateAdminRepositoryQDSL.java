package com.community.debate.repository.master;

import com.community.comment.entity.QCommentEntity;
import com.community.debate.entity.DebatePinRequestEntity;
import com.community.debate.entity.QDebatePinRequestEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** 관리자 토론 줄의 대기 고정 요청 수 — 지우거나 숨긴 의견의 요청은 뺀다(의견 서랍의 대기 목록과 같은 기준). 댓글 표는 post_id 해시 파티션이라 글 ID 로 조각을 고른다 */
@Repository
public class DebateAdminRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QDebatePinRequestEntity REQUEST = QDebatePinRequestEntity.debatePinRequestEntity;
    private static final QCommentEntity COMMENT = QCommentEntity.commentEntity;

    public DebateAdminRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 글 ID → 대기 고정 요청 수(없는 글은 빠진다) */
    public Map<Long, Integer> pendingPinRequests(Collection<Long> postIds, Instant now) {
        Map<Long, Integer> out = new HashMap<>();
        if (postIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(REQUEST.postId, REQUEST.id.count()).from(REQUEST, COMMENT)
                .where(REQUEST.postId.in(postIds), REQUEST.status.eq(DebatePinRequestEntity.PENDING), COMMENT.postId.in(postIds),
                        COMMENT.postId.eq(REQUEST.postId), COMMENT.id.eq(REQUEST.commentId), COMMENT.deletedAt.isNull(),
                        COMMENT.hiddenAt.isNull().or(COMMENT.hiddenUntil.isNotNull().and(COMMENT.hiddenUntil.loe(now))))
                .groupBy(REQUEST.postId).fetch()) {
            Long n = t.get(REQUEST.id.count());
            out.put(t.get(REQUEST.postId), n == null ? 0 : n.intValue());
        }
        return out;
    }
}
