package com.community.post.repository.master;

import com.community.group.entity.GroupStatus;
import com.community.group.entity.QGroupEntity;
import com.community.hotdeal.entity.PostDealEntity;
import com.community.hotdeal.entity.QPostDealEntity;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.community.post.dto.PostNeighborData;
import com.community.post.dto.PostRowData;
import com.community.post.dto.WriteRateData;
import com.community.post.entity.PostEntity;
import com.community.post.entity.PostProcessStatus;
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
import java.util.Collection;
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
    private static final QGroupEntity INACTIVE_GROUP = new QGroupEntity("inactiveGroup");
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

    /**
     * 판매자 숨김(사업자 핫딜 일시 정지·중단 — {@link PostEntity#SELLER_PAUSED}·{@link PostEntity#SELLER_STOPPED}, biz-pause-contract 규칙 9) 중이 아닌 조건.
     * 신고·운영 숨김 글은 게시판 목록에 「숨겨진 글」 줄로 남지만, 판매자 숨김 글은 목록에서 아예 빠진다(쪽 건수·전체 수 포함). 판매자 숨김은 기간이 없다.
     */
    public static BooleanExpression notSellerHidden(QPostEntity p, Instant now) {
        return notHidden(p, now).or(p.hiddenReason.notIn(PostEntity.SELLER_PAUSED, PostEntity.SELLER_STOPPED));
    }

    /**
     * 게시판이 섞인 목록(최신글·통합 검색·회원 작성글·인기글)에 나올 수 있는 글 — 일반 글이거나, <b>비멤버에게 보이는 소모임 글</b>
     * ({@code is_public} = 유효 노출 — 공개 소모임의 글·개별 공개된 글, community api-groups.md 8절·11-1)이고 작성자가 나가서 가리지 않았으며 소모임이 비활성이 아니다
     * (비활성 소모임의 글은 글 상세가 403 {@code GROUP_INACTIVE} 라 줄을 보이지 않는다 — 해산한 소모임에 공개로 남은 글은 읽히므로 나온다).
     * <b>비밀글</b>(V166)은 섞인 목록에 나오지 않는다(보는 사람이 글쓴이·운영진이어도 — 그 게시판 목록에서만 본다).
     * 인기글 배치(R__batch_24 {@code popular_rank_candidates})와 통합 검색의 원시 SQL({@code PostSearchQueries})이 같은 판정을 SQL 로 쓴다 — 한쪽을 고치면 같이 고친다.
     */
    public static BooleanExpression feedVisible(QPostEntity p) {
        return feedVisible(p, false);
    }

    /**
     * {@link #feedVisible(QPostEntity)} 에서 비밀글 제외만 고를 수 있는 것 — 회원 작성글(프로필)을 <b>본인</b>이 볼 때만 {@code includeSecret = true}.
     */
    public static BooleanExpression feedVisible(QPostEntity p, boolean includeSecret) {
        BooleanExpression visible = p.groupId.isNull().or(p.publicPost.isTrue().and(p.groupLeftAt.isNull())
                .and(p.groupId.notIn(JPAExpressions.select(INACTIVE_GROUP.id).from(INACTIVE_GROUP).where(INACTIVE_GROUP.status.eq(GroupStatus.INACTIVE)))));
        return includeSecret ? visible : visible.and(p.secret.isFalse());
    }

    /**
     * 비밀글 거름(같은 게시판의 이전·다음 글) — 그 게시판 운영진이면 null(거르지 않음), 회원이면 비밀글이 아니거나 내 글, 비회원이면 비밀글이 아닌 글만.
     *
     * @param viewerId 보는 회원(비회원 null)
     * @param staff    보는 사람이 그 게시판 매니저·스텝·운영자 회원 계정인가
     */
    public static BooleanExpression secretVisible(QPostEntity p, Long viewerId, boolean staff) {
        if (staff) {
            return null;
        }
        return viewerId == null ? p.secret.isFalse() : p.secret.isFalse().or(p.authorId.eq(viewerId));
    }

    /**
     * 처리 상태 거르기(게시판 목록 {@code ?status=}) — {@code RECEIVED} 는 값 없는 글(상태를 켜기 전에 쓴 글)도 포함한다. null 이면 거르지 않는다.
     */
    public static BooleanExpression processStatusIs(QPostEntity p, PostProcessStatus status) {
        if (status == null) {
            return null;
        }
        BooleanExpression same = p.processStatus.eq(status.name());
        return status == PostProcessStatus.RECEIVED ? same.or(p.processStatus.isNull()) : same;
    }

    /**
     * 19금 글 거름(adult-content-contract) — 성인(로그인 + 본인인증 + 만 19세 이상)이면 null(거르지 않음), 아니면 19금이 아닌 글만. 목록에서 <b>아예 뺀다</b>(자리·건수도 없다).
     * 통합 검색의 원시 SQL({@code PostSearchQueries})이 같은 판정을 SQL 로 쓴다.
     */
    public static BooleanExpression adultVisible(QPostEntity p, boolean adultViewer) {
        return adultViewer ? null : p.adult.isFalse();
    }

    private static ConstructorExpression<PostRowData> row() {
        return Projections.constructor(PostRowData.class, POST.id, POST.boardId, POST.prefixId, POST.authorId, POST.guestName, POST.authorIp, POST.title,
                POST.hasImage, POST.adult, POST.viewCount, POST.likeCount, POST.commentCount, POST.pinnedAt, POST.hiddenAt, POST.hiddenUntil, POST.hiddenReason, POST.createdAt, POST.groupId,
                POST.secret, POST.processStatus, POST.anonymous);
    }

    /** 고정 공지 — 고정한 차례 최신순, 최대 {@code max} 개 */
    public List<PostRowData> pinned(long boardId, int max, boolean adultViewer, Instant now) {
        if (max <= 0) {
            return List.of();
        }
        return query.select(row()).from(POST)
                .where(POST.boardId.eq(boardId), POST.pinnedAt.isNotNull(), alive(POST, now), adultVisible(POST, adultViewer))
                .orderBy(POST.pinnedAt.desc(), POST.id.desc())
                .limit(max)
                .fetch();
    }

    /**
     * 손님에게 보이는 최근 글 — 공지 띠(BOARD 출처, {@code SiteNoticeBarReader})가 쓴다. 보이는 글({@link #alive})이고 숨기지 않았고({@link #notHidden} — 게시판 목록의
     * 「숨겨진 글」 줄도 빼고) 19금이 아닌({@link #adultVisible} 비성인) 글, 등록 최신순(id 내림차순). 게시판 판정(켜짐·소모임 아님)은 부르는 쪽이 한다.
     */
    public List<PostRowData> guestLatest(long boardId, int limit, Instant now) {
        if (limit <= 0) {
            return List.of();
        }
        return query.select(row()).from(POST)
                .where(POST.boardId.eq(boardId), alive(POST, now), notHidden(POST, now), feedVisible(POST), adultVisible(POST, false))
                .orderBy(POST.id.desc())
                .limit(limit)
                .fetch();
    }

    /**
     * 글 목록 한 쪽.
     *
     * @param prefixId      말머리 — null 이면 전체
     * @param q             검색어(제목·본문[HTML 글은 태그를 뗀 평문 칸]·작성자 이름, 대소문자 무시) — null 이면 검색하지 않는다. 숨긴 글은 검색 결과에서 뺀다
     * @param oldest        오래된순(아니면 최신순)
     * @param excludePinned 고정 공지를 목록에서 뺀다(첫 쪽 위에 따로 보일 때)
     * @param adultViewer   보는 사람이 성인인가 — 아니면 19금 글을 뺀다({@link #adultVisible})
     */
    public Page page(long boardId, Long prefixId, String q, boolean oldest, boolean excludePinned, boolean adultViewer, long offset, int limit, Instant now) {
        return page(boardId, prefixId, null, q, oldest, excludePinned, adultViewer, offset, limit, now);
    }

    /**
     * 글 목록 한 쪽 — 처리 상태 거르기까지({@link #processStatusIs}, null = 전체). 비밀글은 목록에 줄로 남는다(내용 칸을 비우는 것은 부르는 쪽).
     */
    public Page page(long boardId, Long prefixId, PostProcessStatus status, String q, boolean oldest, boolean excludePinned, boolean adultViewer, long offset,
                     int limit, Instant now) {
        BooleanExpression where = POST.boardId.eq(boardId).and(alive(POST, now)).and(notSellerHidden(POST, now)).and(adultVisible(POST, adultViewer));
        if (prefixId != null) {
            where = where.and(POST.prefixId.eq(prefixId));
        }
        if (status != null) {
            where = where.and(processStatusIs(POST, status));
        }
        if (excludePinned) {
            where = where.and(POST.pinnedAt.isNull());
        }
        if (q != null) {
            where = where.and(search(q, now));
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

    /** 게시판 안 검색 — 숨긴 글은 빼고 제목·본문(평문 칸)·비회원 이름·회원 닉네임(대소문자 무시). 소모임 글 목록도 같은 검색을 쓴다({@code GroupPostQueries#page}) */
    public static BooleanExpression search(String q, Instant now) {
        QProfileEntity profile = QProfileEntity.profileEntity;
        return notHidden(POST, now).and(POST.title.containsIgnoreCase(q)
                .or(POST.contentText.coalesce(POST.content).containsIgnoreCase(q))
                .or(POST.guestName.containsIgnoreCase(q))
                .or(POST.anonymous.isFalse().and(POST.authorId.in(JPAExpressions.select(profile.userId).from(profile).where(profile.nickname.containsIgnoreCase(q)))))); // 익명 글은 닉네임으로 찾지 않는다(V184)
    }

    /** 핫딜 목록 정렬 — 최신 · 인기(추천) · 마감 임박 */
    public enum DealSort { LATEST, POPULAR, ENDING }

    /**
     * 핫딜 게시판 글 목록 한 쪽(hotdeal-contract 「API 후보」·메모 1-5) — 차례는 진행 중 → 의심 → 끝남(종료·기간 종료), 그 안에서 정렬 키.
     * 마감 임박은 마감 있는 글의 마감 빠른 차례, 마감 없는 글은 그 뒤(기간 종료 = 등록 + 설정 일수 차례 = 등록 차례). 딜 줄이 없는 옛 글은 끝남으로 본다.
     * 상태 판정은 {@code DealViews#view} 와 같다(한쪽을 고치면 같이 고친다).
     *
     * @param hideEnded 종료·기간 종료(와 딜 줄이 없는 글)를 뺀다
     * @param store     쇼핑몰 이름(정확히 같음) — null 이면 거르지 않는다
     */
    public Page dealPage(long boardId, Long prefixId, String q, DealSort sort, boolean hideEnded, String store, boolean excludePinned, int autoExpireDays, long offset,
                         int limit, Instant now) {
        QPostDealEntity deal = QPostDealEntity.postDealEntity;
        BooleanExpression expired = deal.endsAt.isNotNull().and(deal.endsAt.loe(now));
        if (autoExpireDays > 0) {
            expired = expired.or(deal.endsAt.isNull().and(deal.createdAt.loe(now.minus(java.time.Duration.ofDays(autoExpireDays)))));
        }
        BooleanExpression ended = deal.postId.isNull().or(deal.status.eq(PostDealEntity.ENDED)).or(expired);
        BooleanExpression where = POST.boardId.eq(boardId).and(alive(POST, now)).and(notSellerHidden(POST, now));
        if (prefixId != null) {
            where = where.and(POST.prefixId.eq(prefixId));
        }
        if (excludePinned) {
            where = where.and(POST.pinnedAt.isNull());
        }
        if (q != null) {
            where = where.and(search(q, now));
        }
        if (hideEnded) {
            where = where.and(ended.not());
        }
        if (store != null) {
            where = where.and(deal.storeName.eq(store));
        }
        Long total = query.select(POST.id.count()).from(POST).leftJoin(deal).on(deal.postId.eq(POST.id)).where(where).fetchOne();
        if (total == null || total == 0 || offset >= total) {
            return new Page(List.of(), total == null ? 0 : total);
        }
        NumberExpression<Integer> rank = new CaseBuilder().when(ended).then(2).when(deal.flaggedAt.isNotNull()).then(1).otherwise(0);
        List<OrderSpecifier<?>> order = new java.util.ArrayList<>();
        order.add(rank.asc());
        switch (sort) {
            case POPULAR -> order.add(POST.likeCount.desc());
            case ENDING -> {
                order.add(new CaseBuilder().when(deal.endsAt.isNull()).then(1).otherwise(0).asc());
                order.add(deal.endsAt.asc());
                order.add(deal.createdAt.asc());
            }
            case LATEST -> {
                // id 차례만
            }
        }
        order.add(POST.id.desc());
        List<PostRowData> items = query.select(row()).from(POST).leftJoin(deal).on(deal.postId.eq(POST.id)).where(where)
                .orderBy(order.toArray(OrderSpecifier[]::new))
                .offset(offset).limit(limit)
                .fetch();
        return new Page(items, total);
    }

    /** 바로 앞(더 오래된) 글 — 같은 게시판, 보이고 숨기지 않은 글(성인이 아니면 19금 글을 건너뛴다) */
    public Optional<PostNeighborData> older(long boardId, long id, boolean adultViewer, Instant now) {
        return older(boardId, id, adultViewer, null, true, now);
    }

    /**
     * 바로 앞(더 오래된) 글 — 읽을 수 없는 비밀글도 건너뛴다({@link #secretVisible}).
     *
     * @param viewerId 보는 회원(비회원 null)
     * @param staff    보는 사람이 그 게시판 운영진인가(비밀글을 모두 읽는다)
     */
    public Optional<PostNeighborData> older(long boardId, long id, boolean adultViewer, Long viewerId, boolean staff, Instant now) {
        return Optional.ofNullable(query.select(Projections.constructor(PostNeighborData.class, POST.id, POST.title)).from(POST)
                .where(POST.boardId.eq(boardId), POST.id.lt(id), alive(POST, now), notHidden(POST, now), adultVisible(POST, adultViewer),
                        secretVisible(POST, viewerId, staff))
                .orderBy(POST.id.desc()).fetchFirst());
    }

    /** 바로 뒤(더 새) 글 */
    public Optional<PostNeighborData> newer(long boardId, long id, boolean adultViewer, Instant now) {
        return newer(boardId, id, adultViewer, null, true, now);
    }

    /** 바로 뒤(더 새) 글 — 읽을 수 없는 비밀글도 건너뛴다 */
    public Optional<PostNeighborData> newer(long boardId, long id, boolean adultViewer, Long viewerId, boolean staff, Instant now) {
        return Optional.ofNullable(query.select(Projections.constructor(PostNeighborData.class, POST.id, POST.title)).from(POST)
                .where(POST.boardId.eq(boardId), POST.id.gt(id), alive(POST, now), notHidden(POST, now), adultVisible(POST, adultViewer),
                        secretVisible(POST, viewerId, staff))
                .orderBy(POST.id.asc()).fetchFirst());
    }

    /** 본문에 YouTube 표식이 있는 글의 본문(글 ID → 본문) — 이미지 없는 글의 목록 썸네일을 영상에서 고른다 */
    public List<Tuple> youtubeContents(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return query.select(POST.id, POST.content).from(POST).where(POST.id.in(ids), POST.content.contains("data-youtube-id=\"")).fetch();
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

    /** {@code since} 뒤에 같은 작성자가 같은 제목·보이는 본문(HTML 글은 평문 칸)으로 쓴 글이 있는가(지운 글 제외) */
    public boolean duplicate(Long authorId, String ip, Instant since, String title, String plainContent) {
        return query.selectOne().from(POST)
                .where(author(authorId, ip), POST.id.goe(SnowflakeIdGenerator.floorIdAt(since)), POST.createdAt.goe(since), POST.deletedAt.isNull(),
                        POST.title.eq(title), POST.contentText.coalesce(POST.content).eq(plainContent))
                .fetchFirst() != null;
    }
}
