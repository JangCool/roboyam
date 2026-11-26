package com.community.highlight.repository.master;

import com.community.post.entity.QPostEntity;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 게시판 추천 글의 자동 채움 질의(board-recommend-contract 「규칙」) — 한 게시판에서 {@code since} 뒤에 쓴 글을 추천순(같으면 최신 글 먼저)으로.
 * 거름은 팁 묶음 헤드라인 채우기({@code TipsPostQueries#top})와 같다: 보이는 글 · 신고 숨김 아님 · 섞인 목록에 나올 수 있는 글(비밀글·비멤버에게 안 보이는 소모임 글 제외) ·
 * 19금 아님 · 공지(고정) 아님. 그 위에 추천 1 이상. 「최근」은 {@code created_at ≥ since} 이고 id 범위({@link SnowflakeIdGenerator#floorIdAt})로 글 월 조각을 가지치기한다.
 * 결과는 게시판마다 짧게 캐시된다({@code boards.recommended}).
 */
@Repository
public class BoardRecommendQueries extends IrobitRepositoryQDSL {

    private static final QPostEntity POST = QPostEntity.postEntity;

    public BoardRecommendQueries(JPAQueryFactory query) {
        super(query);
    }

    /** 카드에 쓰는 칸만 */
    public record Row(Long id, String title, Integer likeCount, Long prefixId) {
    }

    /**
     * @param exclude 뺄 글(이미 고른 것)
     */
    public List<Row> mostLiked(long boardId, Instant since, Collection<Long> exclude, int limit, Instant now) {
        if (limit <= 0) {
            return List.of();
        }
        BooleanExpression where = POST.boardId.eq(boardId).and(PostRepositoryQDSL.alive(POST, now)).and(PostRepositoryQDSL.notHidden(POST, now))
                .and(PostRepositoryQDSL.feedVisible(POST)).and(POST.adult.isFalse()).and(POST.pinnedAt.isNull()).and(POST.likeCount.goe(1))
                .and(POST.id.goe(SnowflakeIdGenerator.floorIdAt(since))).and(POST.createdAt.goe(since));
        if (!exclude.isEmpty()) {
            where = where.and(POST.id.notIn(exclude));
        }
        return query.select(Projections.constructor(Row.class, POST.id, POST.title, POST.likeCount, POST.prefixId)).from(POST).where(where)
                .orderBy(POST.likeCount.desc(), POST.id.desc()).limit(limit).fetch();
    }
}
