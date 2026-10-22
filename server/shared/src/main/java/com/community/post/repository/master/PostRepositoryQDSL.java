package com.community.post.repository.master;

import com.community.post.dto.PostNeighborData;
import com.community.post.dto.PostRowData;
import com.community.post.dto.WriteRateData;
import com.community.post.entity.PostEntity;
import com.community.post.entity.QPostEntity;
import com.community.user.entity.QProfileEntity;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 공개 게시판의 글 질의 — 목록·고정 공지·이전/다음 글·작성 속도. 글 표는 id(Snowflake = 시각) 월 범위 파티션이라 시각 범위 조건에는 id 범위를 함께 걸어
 * 조각을 가지치기한다({@link SnowflakeIdGenerator#floorIdAt}).
 *
 * <p><b>보이는 글</b>({@link #alive}): 지우지 않음 · 공개(PUBLISHED) · 예약 삭제 시각 전. 신고로 숨긴 글은 목록에 <b>자리만</b> 남으므로 목록 조건에서 빼지 않는다
 * (검색·이전/다음 글에서는 뺀다). 앱의 {@code PostEntity#isGoneAt}·{@code #isHiddenAt} 과 같은 기준이다.
 */
@Repository
public class PostRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QPostEntity POST = QPostEntity.postEntity;
    /** 검색어 최대 길이 — 넘으면 자른다 */
    public static final int QUERY_MAX = 50;

    public PostRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 한 쪽 결과 */
    public record Page(List<PostRowData> items, long total) {
    }

    /** 보이는 글 조건 */
    public static BooleanExpression alive(QPostEntity p, Instant now) {
        return p.deletedAt.isNull().and(p.status.eq(PostEntity.PUBLISHED)).and(p.deleteScheduledAt.isNull().or(p.deleteScheduledAt.gt(now)));
    }

    /** 신고 숨김 중이 아닌 조건 */
    public static BooleanExpression notHidden(QPostEntity p, Instant now) {
        return p.hiddenAt.isNull().or(p.hiddenUntil.isNotNull().and(p.hiddenUntil.loe(now)));
    }

    private static ConstructorExpression<PostRowData> row() {
        return Projections.constructor(PostRowData.class, POST.id, POST.boardId, POST.prefixId, POST.authorId, POST.guestName, POST.authorIp, POST.title,
                POST.hasImage, POST.adult, POST.viewCount, POST.likeCount, POST.commentCount, POST.pinnedAt, POST.hiddenAt, POST.hiddenUntil, POST.createdAt);
    }

    /** 고정 공지 — 고정한 차례 최신순, 최대 {@code max} 개 */
    public List<PostRowData> pinned(long boardId, int max, Instant now) {
        if (max <= 0) {
            return List.of();
        }
        return query.select(row()).from(POST)
                .where(POST.boardId.eq(boardId), POST.pinnedAt.isNotNull(), alive(POST, now))
                .orderBy(POST.pinnedAt.desc(), POST.id.desc())
                .limit(max)
                .fetch();
    }

    /**
     * 글 목록 한 쪽.
     *
     * @param prefixId      말머리 — null 이면 전체
     * @param q             검색어(제목·본문·작성자 이름, 대소문자 무시) — null 이면 검색하지 않는다. 숨긴 글은 검색 결과에서 뺀다
     * @param oldest        오래된순(아니면 최신순)
     * @param excludePinned 고정 공지를 목록에서 뺀다(첫 쪽 위에 따로 보일 때)
     */
    public Page page(long boardId, Long prefixId, String q, boolean oldest, boolean excludePinned, long offset, int limit, Instant now) {
        BooleanExpression where = POST.boardId.eq(boardId).and(alive(POST, now));
        if (prefixId != null) {
            where = where.and(POST.prefixId.eq(prefixId));
        }
        if (excludePinned) {
            where = where.and(POST.pinnedAt.isNull());
        }
        if (q != null) {
            QProfileEntity profile = QProfileEntity.profileEntity;
            where = where.and(notHidden(POST, now)).and(POST.title.containsIgnoreCase(q)
                    .or(POST.content.containsIgnoreCase(q))
                    .or(POST.guestName.containsIgnoreCase(q))
                    .or(POST.authorId.in(JPAExpressions.select(profile.userId).from(profile).where(profile.nickname.containsIgnoreCase(q)))));
        }
        Long total = query.select(POST.id.count()).from(POST).where(where).fetchOne();
        if (total == null || total == 0 || offset >= total) {
            return new Page(List.of(), total == null ? 0 : total);
        }
        List<PostRowData> items = query.select(row()).from(POST).where(where)
                .orderBy(oldest ? POST.id.asc() : POST.id.desc())
                .offset(offset).limit(limit)
                .fetch();
        return new Page(items, total);
    }

    /** 바로 앞(더 오래된) 글 — 같은 게시판, 보이고 숨기지 않은 글 */
    public Optional<PostNeighborData> older(long boardId, long id, Instant now) {
        return Optional.ofNullable(query.select(Projections.constructor(PostNeighborData.class, POST.id, POST.title)).from(POST)
                .where(POST.boardId.eq(boardId), POST.id.lt(id), alive(POST, now), notHidden(POST, now))
                .orderBy(POST.id.desc()).fetchFirst());
    }

    /** 바로 뒤(더 새) 글 */
    public Optional<PostNeighborData> newer(long boardId, long id, Instant now) {
        return Optional.ofNullable(query.select(Projections.constructor(PostNeighborData.class, POST.id, POST.title)).from(POST)
                .where(POST.boardId.eq(boardId), POST.id.gt(id), alive(POST, now), notHidden(POST, now))
                .orderBy(POST.id.asc()).fetchFirst());
    }

    /** 같은 작성자 조건 — 회원은 회원 ID, 비회원은 작성 IP */
    private static BooleanExpression author(Long authorId, String ip) {
        return authorId != null ? POST.authorId.eq(authorId) : POST.authorId.isNull().and(POST.authorIp.eq(ip));
    }

    /** {@code since} 뒤에 같은 작성자가 쓴 글 수와 마지막 시각(지운 글 포함 — 지우고 다시 쓰기로 한도를 피하지 못하게) */
    public WriteRateData rate(Long authorId, String ip, Instant since) {
        Tuple t = query.select(POST.id.count(), POST.createdAt.max()).from(POST)
                .where(author(authorId, ip), POST.id.goe(SnowflakeIdGenerator.floorIdAt(since)), POST.createdAt.goe(since))
                .fetchOne();
        long count = t == null || t.get(0, Long.class) == null ? 0 : t.get(0, Long.class);
        return new WriteRateData(count, t == null ? null : t.get(1, Instant.class));
    }

    /** {@code since} 뒤에 같은 작성자가 같은 제목·본문으로 쓴 글이 있는가(지운 글 제외) */
    public boolean duplicate(Long authorId, String ip, Instant since, String title, String content) {
        return query.selectOne().from(POST)
                .where(author(authorId, ip), POST.id.goe(SnowflakeIdGenerator.floorIdAt(since)), POST.createdAt.goe(since), POST.deletedAt.isNull(),
                        POST.title.eq(title), POST.content.eq(content))
                .fetchFirst() != null;
    }
}
