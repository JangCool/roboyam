package com.community.post.repository.master;

import com.community.post.dto.PostRowData;
import com.community.post.entity.QPostEntity;
import com.community.post.entity.QPostTagEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 게시판이 섞인 글 목록의 질의 — 최신글 전체({@code GET /api/home/latest})와 글 ID 로 줄 읽기(인기글 스냅샷·통합 검색 결과).
 * 줄 모양은 게시판 글 목록과 같은 {@link PostRowData}(본문은 읽지 않는다 — 투영은 {@code PostRepositoryQDSL#row} 와 같은 칸 순서).
 *
 * <p><b>실행 계획</b>: 글 표는 id(Snowflake) 월 범위 파티션이다. 최신글은 {@code ORDER BY id DESC LIMIT n} 이라 조각마다의 기본 키 색인을 최신 조각부터 거꾸로 읽다가
 * 개수를 채우면 멈춘다(정렬된 Append — 범위 파티션 키 순서와 같아 MergeAppend 가 필요 없다). 커서({@code id < cursor})는 그보다 새 조각을 계획 때 잘라 낸다.
 * 보임·게시판 조건은 읽은 행에 거는 거름이다(꺼진 게시판·숨김·지운 글이 많지 않은 한 몇 행 더 읽을 뿐이다).
 */
@Repository
public class PostFeedRepositoryQDSL extends IrobitRepositoryQDSL {

    private static final QPostEntity POST = QPostEntity.postEntity;

    public PostFeedRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    private static ConstructorExpression<PostRowData> row() {
        return Projections.constructor(PostRowData.class, POST.id, POST.boardId, POST.prefixId, POST.authorId, POST.guestName, POST.authorIp, POST.title,
                POST.hasImage, POST.adult, POST.viewCount, POST.likeCount, POST.commentCount, POST.pinnedAt, POST.hiddenAt, POST.hiddenUntil, POST.hiddenReason, POST.createdAt, POST.groupId,
                POST.secret, POST.processStatus, POST.anonymous);
    }

    /**
     * 최신글 — 주어진 게시판의 보이고 숨기지 않은 글, id(= 작성 시각) 내림차순.
     * 게시판 고르기(켜진 게시판 · 「가젯 노출 금지」에 최신글 LATEST 를 고르지 않은 게시판)는 부르는 쪽({@code LatestFeedService})이 게시판 사본으로 한다.
     *
     * @param beforeId 이 ID 보다 오래된 글만(커서) — null 이면 처음부터
     * @param limit    읽을 줄 수(다음 쪽이 있는지 보려면 쪽 크기 + 1)
     * @param adultViewer 보는 사람이 성인인가 — 아니면 19금 글을 뺀다({@link PostRepositoryQDSL#adultVisible})
     */
    public List<PostRowData> latest(Collection<Long> boardIds, Long beforeId, int limit, boolean adultViewer, Instant now) {
        if (boardIds.isEmpty()) {
            return List.of();
        }
        BooleanExpression where = POST.boardId.in(boardIds).and(PostRepositoryQDSL.alive(POST, now)).and(PostRepositoryQDSL.notHidden(POST, now))
                .and(PostRepositoryQDSL.feedVisible(POST)).and(PostRepositoryQDSL.adultVisible(POST, adultViewer));
        if (beforeId != null) {
            where = where.and(POST.id.lt(beforeId));
        }
        return query.select(row()).from(POST).where(where).orderBy(POST.id.desc()).limit(limit).fetch();
    }

    /**
     * 태그가 달린 최신글({@link #latest} 와 같은 거름) — 글 목록 가젯 태그 탭(post-tags-contract). 태그 행에서 출발한다: 색인 (tag_key, post_id) 를 글 ID 내림차순으로 읽으며
     * 글 행을 붙이고 거름을 건 뒤 개수를 채우면 멈춘다(드문 태그도 글 표 전체를 훑지 않는다).
     *
     * @param tagKey 태그 비교 열쇠({@code PostTags#key})
     */
    public List<PostRowData> latestTagged(Collection<Long> boardIds, String tagKey, int limit, boolean adultViewer, Instant now) {
        if (boardIds.isEmpty() || tagKey == null || tagKey.isEmpty()) {
            return List.of();
        }
        QPostTagEntity tag = QPostTagEntity.postTagEntity;
        BooleanExpression where = tag.tagKey.eq(tagKey).and(POST.boardId.in(boardIds)).and(PostRepositoryQDSL.alive(POST, now)).and(PostRepositoryQDSL.notHidden(POST, now))
                .and(PostRepositoryQDSL.feedVisible(POST)).and(PostRepositoryQDSL.adultVisible(POST, adultViewer));
        return query.select(row()).from(tag).join(POST).on(POST.id.eq(tag.postId)).where(where).orderBy(tag.postId.desc()).limit(limit).fetch();
    }

    /**
     * 글 ID 로 줄 읽기 — 지금 보이고 숨기지 않은 글만(게시판 판정은 부르는 쪽). 순서는 정하지 않는다. 소모임 글은 지금 비멤버에게 보이는 것만
     * ({@link PostRepositoryQDSL#feedVisible} — 인기글 스냅샷 뒤에 멤버 전용으로 돌린 글은 빠진다). 성인이 아니면 19금 글도 빠진다
     */
    public List<PostRowData> visibleRows(Collection<Long> ids, boolean adultViewer, Instant now) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return query.select(row()).from(POST)
                .where(POST.id.in(ids), PostRepositoryQDSL.alive(POST, now), PostRepositoryQDSL.notHidden(POST, now),
                        PostRepositoryQDSL.feedVisible(POST), PostRepositoryQDSL.adultVisible(POST, adultViewer))
                .fetch();
    }
}
