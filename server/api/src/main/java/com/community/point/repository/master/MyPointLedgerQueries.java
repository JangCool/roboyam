package com.community.point.repository.master;

import com.community.comment.entity.QCommentEntity;
import com.community.point.entity.PointLedgerEntity;
import com.community.point.entity.QPointLedgerEntity;
import com.community.point.entity.QPointLedgerLotEntity;
import com.community.point.entity.QPointLotEntity;
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
    private static final QPointLedgerLotEntity LEDGER_LOT = QPointLedgerLotEntity.pointLedgerLotEntity;
    private static final QPointLotEntity LOT = QPointLotEntity.pointLotEntity;
    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final QCommentEntity COMMENT = QCommentEntity.commentEntity;

    public MyPointLedgerQueries(JPAQueryFactory query) {
        super(query);
    }

    /**
     * 본인 줄 한 쪽 — 최신이 위(id 내림차순), {@code limit + 1} 줄까지.
     *
     * @param kind     {@code EARN}·{@code USE}·{@code REVOKE}·{@code CANCEL_USE}·{@code EXPIRE} — null 이면 전체
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

    /**
     * 원장 줄 ID → 그 줄이 <b>새로 만든</b> 되돌림 묶음({@code restored_from_lot_id} 있음 — 원래 묶음이 만료돼 {@code restored_lot_valid_days} 짜리로 돌려준 사용 취소·글 복구)의
     * 가장 이른 소멸 시각. 그런 묶음이 없는 줄은 빠진다. 「그 줄이 만든」 = 그 줄이 묶음에 더했고(반영 +) 묶음의 출처(사유·대상)가 그 줄과 같다 — 되돌림 줄은 (사유, 대상)마다 하나라서,
     * 나중에 다른 사용 취소가 그 되돌림 묶음에 다시 돌려준 경우(원래 묶음이 아직 살아 있음)는 빠진다(색인: {@code point_ledger_lots} PK 의 앞 칸 ledger_id).
     */
    public Map<Long, Instant> restoredUntil(Collection<Long> ledgerIds) {
        Map<Long, Instant> out = new HashMap<>();
        if (ledgerIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(LEDGER_LOT.ledgerId, LOT.expiresAt.min()).from(LEDGER_LOT).join(LOT).on(LOT.id.eq(LEDGER_LOT.lotId))
                .join(LEDGER).on(LEDGER.id.eq(LEDGER_LOT.ledgerId))
                .where(LEDGER_LOT.ledgerId.in(ledgerIds), LEDGER_LOT.amount.gt(0L), LOT.restoredFromLotId.isNotNull(), LOT.expiresAt.isNotNull(),
                        LOT.sourceReason.eq(LEDGER.reason), LOT.sourceRefType.eq(LEDGER.refType), LOT.sourceRefId.eq(LEDGER.refId))
                .groupBy(LEDGER_LOT.ledgerId).fetch()) {
            out.put(t.get(LEDGER_LOT.ledgerId), t.get(LOT.expiresAt.min()));
        }
        return out;
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
