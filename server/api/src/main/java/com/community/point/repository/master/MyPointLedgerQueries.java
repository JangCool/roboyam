package com.community.point.repository.master;

import com.community.comment.entity.QCommentEntity;
import com.community.point.entity.PointLedgerEntity;
import com.community.point.entity.QPointLedgerEntity;
import com.community.post.entity.QPostEntity;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * 내 포인트 내역 질의 — 원장 본인 줄(색인 {@code ix_point_ledger_user (user_id, id DESC)}) + 줄이 가리키는 글의 제목(볼 수 있는 글만).
 */
@Repository
public class MyPointLedgerQueries extends RoboyamRepositoryQDSL {

    private static final QPointLedgerEntity LEDGER = QPointLedgerEntity.pointLedgerEntity;
    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final QCommentEntity COMMENT = QCommentEntity.commentEntity;

    public MyPointLedgerQueries(JPAQueryFactory query) {
        super(query);
    }

    /**
     * 본인 줄 한 쪽 — 최신이 위(id 내림차순), {@code limit + 1} 줄까지.
     *
     * @param kind     {@code EARN}·{@code USE}·{@code REVOKE} — null 이면 전체
     * @param beforeId 이 id 보다 오래된 줄만 — null 이면 처음부터
     */
    public List<PointLedgerEntity> page(long userId, String kind, Long beforeId, int limit) {
        BooleanExpression where = LEDGER.userId.eq(userId);
        if (kind != null) {
            where = where.and(LEDGER.kind.eq(kind));
        }
        if (beforeId != null) {
            where = where.and(LEDGER.id.lt(beforeId));
        }
        return query.selectFrom(LEDGER).where(where).orderBy(LEDGER.id.desc()).limit(limit + 1L).fetch();
    }

    /** 댓글 ID → 그 댓글이 달린 글 ID(댓글 표는 글 해시 조각이라 조각마다 {@code ix_comments_id} 를 찌른다) */
    public Map<Long, Long> postIdsOfComments(Collection<Long> commentIds) {
        Map<Long, Long> out = new HashMap<>();
        if (commentIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(COMMENT.id, COMMENT.postId).from(COMMENT).where(COMMENT.id.in(commentIds)).fetch()) {
            out.put(t.get(COMMENT.id), t.get(COMMENT.postId));
        }
        return out;
    }

    /** 볼 수 있는 글(공개 목록에 보임 · 숨김 아님 · 켜진 게시판)의 제목 — 볼 수 없는 글은 빠진다 */
    public Map<Long, String> visibleTitles(Collection<Long> postIds, Collection<Long> boardIds, Instant now) {
        Map<Long, String> out = new HashMap<>();
        if (postIds.isEmpty() || boardIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(POST.id, POST.title).from(POST)
                .where(POST.id.in(postIds), PostRepositoryQDSL.alive(POST, now), PostRepositoryQDSL.notHidden(POST, now), POST.boardId.in(boardIds)).fetch()) {
            out.put(t.get(POST.id), t.get(POST.title));
        }
        return out;
    }
}
