package com.community.sitemap.repository.master;

import com.community.group.entity.GroupStatus;
import com.community.group.entity.QGroupEntity;
import com.community.post.entity.QPostEntity;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.sitemap.dto.SitemapPostData;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 사이트맵에 넣을 글의 질의(docs/features/sitemap-contract.md) — 비회원이 글 보기로 열 수 있는 글의 ID·시각만 읽는다(제목·본문은 읽지 않는다).
 * 글 하나의 판정({@code PostLinkPreviewService} — 지움·숨김·비밀글·19금·{@code PostOpenRules})을 조건으로 옮긴 것이라 한쪽을 고치면 같이 고친다.
 *
 * <p><b>실행 계획</b>: {@code ORDER BY id} 라 파티션(id 월 범위)마다의 기본 키 색인을 오래된 조각부터 읽는다. 뒤쪽 묶음은 앞 묶음을 지나가야 하지만(OFFSET)
 * 부르는 쪽이 크롤러뿐이고 한 시간 캐시라 묶음 경계를 따로 기억하지 않는다.
 */
@Repository
public class SitemapPostRepositoryQDSL extends IrobitRepositoryQDSL {

    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final QGroupEntity GROUP = QGroupEntity.groupEntity;

    public SitemapPostRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /**
     * 넣을 글 수.
     *
     * @param boardIds      일반 글을 넣어도 되는 공개 게시판
     * @param groupBoardIds 소모임 글을 넣어도 되는 소모임 게시판
     */
    public long count(Collection<Long> boardIds, Collection<Long> groupBoardIds, Instant now) {
        BooleanExpression where = listed(boardIds, groupBoardIds, now);
        if (where == null) {
            return 0;
        }
        Long count = query.select(POST.id.count()).from(POST).where(where).fetchOne();
        return count == null ? 0 : count;
    }

    /** 넣을 글 한 묶음 — 글 ID 오름차순({@link #count} 와 같은 조건) */
    public List<SitemapPostData> page(Collection<Long> boardIds, Collection<Long> groupBoardIds, long offset, int limit, Instant now) {
        BooleanExpression where = listed(boardIds, groupBoardIds, now);
        if (where == null) {
            return List.of();
        }
        return query.select(Projections.constructor(SitemapPostData.class, POST.id, POST.editedAt, POST.createdAt)).from(POST)
                .where(where).orderBy(POST.id.asc()).offset(offset).limit(limit).fetch();
    }

    /** 비회원이 열 수 있는 글 — 넣을 게시판이 하나도 없으면 null */
    private static BooleanExpression listed(Collection<Long> boardIds, Collection<Long> groupBoardIds, Instant now) {
        BooleanExpression general = boardIds.isEmpty() ? null : POST.groupId.isNull().and(POST.boardId.in(boardIds));
        // 소모임 글 — 열린 소모임의 비멤버에게 보이는 글(유효 노출 is_public)이고 작성자가 나가서 가린 글이 아니다
        BooleanExpression group = groupBoardIds.isEmpty() ? null : POST.boardId.in(groupBoardIds).and(POST.publicPost.isTrue()).and(POST.groupLeftAt.isNull())
                .and(POST.groupId.in(JPAExpressions.select(GROUP.id).from(GROUP).where(GROUP.status.eq(GroupStatus.ACTIVE))));
        BooleanExpression place = Expressions.anyOf(general, group);
        if (place == null) {
            return null;
        }
        return place.and(PostRepositoryQDSL.alive(POST, now)).and(PostRepositoryQDSL.notHidden(POST, now)).and(POST.secret.isFalse()).and(POST.adult.isFalse());
    }
}
