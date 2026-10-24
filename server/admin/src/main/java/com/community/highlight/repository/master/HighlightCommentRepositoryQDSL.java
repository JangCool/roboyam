package com.community.highlight.repository.master;

import com.community.comment.entity.QCommentEntity;
import com.community.debate.dto.DebateVotesView;
import com.community.debate.entity.DebateStance;
import com.community.highlight.dto.response.HighlightCommentCountsView;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * 하이라이트 글의 원댓글 수(입장별) — 지우거나 숨기지 않은 원댓글을 글·입장으로 묶어 센다. 댓글 표는 post_id 해시 파티션이라 글 ID 로 조각을 고른다.
 * 투표 수({@code votes})는 부르는 쪽이 {@code DebateQueries#votesOf} 로 읽어 넘긴다.
 */
@Repository
public class HighlightCommentRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QCommentEntity COMMENT = QCommentEntity.commentEntity;

    public HighlightCommentRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /**
     * @param visibleAll 글 ID → 보이는 댓글 수({@code posts.comment_count}, 답글 포함) — {@code all} 칸
     * @param votes      글 ID → 투표 수 — {@code votes} 칸(없으면 0)
     * @return 글 ID → 구분별 수(없는 글도 0 으로 채운다)
     */
    public Map<Long, HighlightCommentCountsView> counts(Collection<Long> postIds, Map<Long, Integer> visibleAll, Map<Long, DebateVotesView> votes, Instant now) {
        Map<Long, int[]> acc = new HashMap<>();
        if (!postIds.isEmpty()) {
            for (Tuple t : query.select(COMMENT.postId, COMMENT.stance, COMMENT.id.count()).from(COMMENT)
                    .where(COMMENT.postId.in(postIds), COMMENT.parentId.isNull(), COMMENT.deletedAt.isNull(),
                            COMMENT.hiddenAt.isNull().or(COMMENT.hiddenUntil.isNotNull().and(COMMENT.hiddenUntil.loe(now))))
                    .groupBy(COMMENT.postId, COMMENT.stance).fetch()) {
                int[] c = acc.computeIfAbsent(t.get(COMMENT.postId), k -> new int[4]);
                Long n = t.get(COMMENT.id.count());
                int count = n == null ? 0 : n.intValue();
                String stance = t.get(COMMENT.stance);
                c[stance == null ? 0 : 1 + DebateStance.valueOf(stance).ordinal()] += count; // 0 일반 · 1 PRO · 2 CON · 3 NEUTRAL
            }
        }
        Map<Long, HighlightCommentCountsView> out = new HashMap<>();
        for (Long postId : postIds) {
            int[] c = acc.getOrDefault(postId, new int[4]);
            out.put(postId, new HighlightCommentCountsView(visibleAll.getOrDefault(postId, 0), c[0], c[1], c[2], c[3],
                    votes.getOrDefault(postId, DebateVotesView.EMPTY)));
        }
        return out;
    }
}
