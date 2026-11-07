package com.community.tips.repository.master;

import com.community.post.dto.PostRowData;
import com.community.post.entity.QPostEntity;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 팁·강좌·사용기 묶음({@code /api/tips} — tips-contract 「서버 구현 결과」)의 글 질의. 줄 모양은 섞인 목록과 같은 {@link PostRowData}(본문은 읽지 않는다 —
 * 투영은 {@code PostFeedRepositoryQDSL#row} 와 같은 칸 순서).
 *
 * <ul>
 *   <li>{@link #top}: 헤드라인 채우기·많이 본 글 — 보이는(지움·임시저장·예약 삭제 지남 아님) · 신고 숨김 아님 · 비멤버에게 보임 · 19금 아님 · 공지(고정) 아님.
 *       「최근」은 {@code created_at ≥ since} 이고 id 범위({@link SnowflakeIdGenerator#floorIdAt})로 글 월 조각을 가지치기한다.</li>
 *   <li>{@link #latest}: 최신 글 한 쪽 — 게시판 글 목록({@code PostRepositoryQDSL#page})과 같은 거름(신고 숨긴 글은 자리만 남고, 19금 글은 성인에게만) + 공지(고정) 빼기,
 *       게시판이 섞인 목록이라 비멤버에게 보이는 글만. id(= 작성 시각) 내림차순.</li>
 * </ul>
 */
@Repository
public class TipsPostQueries extends RoboyamRepositoryQDSL {

    private static final QPostEntity POST = QPostEntity.postEntity;

    /** 줄 세우는 수 */
    public enum Rank { LIKES, VIEWS }

    public TipsPostQueries(JPAQueryFactory query) {
        super(query);
    }

    private static ConstructorExpression<PostRowData> row() {
        return Projections.constructor(PostRowData.class, POST.id, POST.boardId, POST.prefixId, POST.authorId, POST.guestName, POST.authorIp, POST.title,
                POST.hasImage, POST.adult, POST.viewCount, POST.likeCount, POST.commentCount, POST.pinnedAt, POST.hiddenAt, POST.hiddenUntil, POST.hiddenReason, POST.createdAt, POST.groupId);
    }

    /**
     * 수가 많은 글 — {@code rank} 차례(추천: 추천 → 조회 → 최신, 조회: 조회 → 추천 → 최신).
     *
     * @param since   기준 시각(null = 기간 없음)
     * @param recent  true = {@code since} 뒤에 쓴 글, false = 그 앞에 쓴 글
     * @param exclude 뺄 글(이미 고른 것)
     */
    public List<PostRowData> top(Collection<Long> boardIds, Rank rank, Instant since, boolean recent, Collection<Long> exclude, int limit, Instant now) {
        if (boardIds.isEmpty() || limit <= 0) {
            return List.of();
        }
        BooleanExpression where = POST.boardId.in(boardIds).and(PostRepositoryQDSL.alive(POST, now)).and(PostRepositoryQDSL.notHidden(POST, now))
                .and(PostRepositoryQDSL.feedVisible(POST)).and(POST.adult.isFalse()).and(POST.pinnedAt.isNull());
        if (since != null) {
            where = recent ? where.and(POST.id.goe(SnowflakeIdGenerator.floorIdAt(since))).and(POST.createdAt.goe(since)) : where.and(POST.createdAt.lt(since));
        }
        if (!exclude.isEmpty()) {
            where = where.and(POST.id.notIn(exclude));
        }
        OrderSpecifier<?>[] order = rank == Rank.LIKES
                ? new OrderSpecifier<?>[] {POST.likeCount.desc(), POST.viewCount.desc(), POST.id.desc()}
                : new OrderSpecifier<?>[] {POST.viewCount.desc(), POST.likeCount.desc(), POST.id.desc()};
        return query.select(row()).from(POST).where(where).orderBy(order).limit(limit).fetch();
    }

    /** 최신 글 한 쪽(공지 빼고) — 전체 수와 함께 */
    public PostRepositoryQDSL.Page latest(Collection<Long> boardIds, boolean adultViewer, long offset, int limit, Instant now) {
        if (boardIds.isEmpty()) {
            return new PostRepositoryQDSL.Page(List.of(), 0);
        }
        BooleanExpression where = POST.boardId.in(boardIds).and(PostRepositoryQDSL.alive(POST, now)).and(PostRepositoryQDSL.notSellerHidden(POST, now))
                .and(PostRepositoryQDSL.feedVisible(POST)).and(PostRepositoryQDSL.adultVisible(POST, adultViewer)).and(POST.pinnedAt.isNull());
        Long total = query.select(POST.id.count()).from(POST).where(where).fetchOne();
        if (total == null || total == 0 || offset >= total) {
            return new PostRepositoryQDSL.Page(List.of(), total == null ? 0 : total);
        }
        List<PostRowData> items = query.select(row()).from(POST).where(where).orderBy(POST.id.desc()).offset(offset).limit(limit).fetch();
        return new PostRepositoryQDSL.Page(items, total);
    }
}
