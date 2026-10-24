package com.community.post.repository.master;

import com.community.post.dto.AdminPostRow;
import com.community.post.entity.QPostEntity;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Repository;

/**
 * 관리자 화면의 글 질의 — 글 ID 로 줄 읽기(상태와 무관), 하이라이트 대상 찾기(제목·글 번호)·후보(추천 많은 글).
 * 「지정할 수 있는 글」 = 보이는 글({@link PostRepositoryQDSL#alive}) · 숨김 아님 · 19금 아님 · 켜진 게시판({@code boardIds} — 부르는 쪽이 게시판 사본으로 고른다).
 * 제목 찾기는 {@code lower(title)} 의 pg_trgm GIN 색인(V68)을 탄다.
 */
@Repository
public class AdminPostRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QPostEntity POST = QPostEntity.postEntity;

    public AdminPostRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    private static ConstructorExpression<AdminPostRow> row() {
        return Projections.constructor(AdminPostRow.class, POST.id, POST.boardId, POST.prefixId, POST.authorId, POST.guestName, POST.title, POST.status,
                POST.adult, POST.viewCount, POST.likeCount, POST.commentCount, POST.hiddenAt, POST.hiddenUntil, POST.deleteScheduledAt, POST.deletedAt,
                POST.createdAt);
    }

    /** 글 ID 로 줄 읽기 — 지운·숨긴 글도. 순서는 정하지 않는다 */
    public List<AdminPostRow> byIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return query.select(row()).from(POST).where(POST.id.in(ids)).fetch();
    }

    private static BooleanExpression assignable(Collection<Long> boardIds, Collection<Long> exceptIds, Instant now) {
        BooleanExpression where = PostRepositoryQDSL.alive(POST, now).and(PostRepositoryQDSL.notHidden(POST, now)).and(POST.adult.isFalse())
                .and(POST.boardId.in(boardIds));
        return exceptIds.isEmpty() ? where : where.and(POST.id.notIn(exceptIds));
    }

    /** 제목에 {@code text} 가 든 지정할 수 있는 글 — 최신순 {@code limit} 개 */
    public List<AdminPostRow> searchTitle(String text, Collection<Long> boardIds, Collection<Long> exceptIds, Instant now, int limit) {
        if (boardIds.isEmpty()) {
            return List.of();
        }
        String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
        return query.select(row()).from(POST)
                .where(assignable(boardIds, exceptIds, now), POST.title.lower().like(pattern, '!'))
                .orderBy(POST.id.desc()).limit(limit).fetch();
    }

    /** 그 번호의 지정할 수 있는 글(없으면 빈 목록) */
    public List<AdminPostRow> assignableById(long id, Collection<Long> boardIds, Collection<Long> exceptIds, Instant now) {
        if (boardIds.isEmpty()) {
            return List.of();
        }
        return query.select(row()).from(POST).where(assignable(boardIds, exceptIds, now), POST.id.eq(id)).fetch();
    }

    /**
     * 하이라이트 후보 — 지정할 수 있는 글 중 추천 {@code minLikes} 이상, 추천 많은 순(같으면 최신) {@code limit} 개.
     *
     * @param since 이 시각 뒤에 쓴 글만(null 이면 전체 기간) — id 범위로 월 조각을 가지치기한다
     */
    public List<AdminPostRow> candidates(Instant since, int minLikes, Collection<Long> boardIds, Collection<Long> exceptIds, Instant now, int limit) {
        if (boardIds.isEmpty()) {
            return List.of();
        }
        BooleanExpression where = assignable(boardIds, exceptIds, now).and(POST.likeCount.goe(minLikes));
        if (since != null) {
            where = where.and(POST.id.goe(SnowflakeIdGenerator.floorIdAt(since))).and(POST.createdAt.goe(since));
        }
        return query.select(row()).from(POST).where(where).orderBy(POST.likeCount.desc(), POST.id.desc()).limit(limit).fetch();
    }

    private static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
