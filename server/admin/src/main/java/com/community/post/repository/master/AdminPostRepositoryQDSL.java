package com.community.post.repository.master;

import com.community.comment.entity.QCommentEntity;
import com.community.debate.entity.QPostDebateEntity;
import com.community.post.dto.AdminPostListRow;
import com.community.post.dto.AdminPostRow;
import com.community.post.dto.AdminPostSearch;
import com.community.post.entity.PostEntity;
import com.community.post.entity.QPostEntity;
import com.community.report.entity.QReportCaseEntity;
import com.community.report.entity.ReportCaseEntity;
import com.community.report.entity.ReportEntity;
import com.community.user.entity.QProfileEntity;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * 관리자 화면의 글 질의 — 글 ID 로 줄 읽기(상태와 무관), 하이라이트 대상 찾기(제목·글 번호)·후보(추천 많은 글).
 * 「지정할 수 있는 글」 = 보이는 글({@link PostRepositoryQDSL#alive}) · 숨김 아님 · 19금 아님 · 켜진 게시판({@code boardIds} — 부르는 쪽이 게시판 사본으로 고른다).
 * 제목 찾기는 {@code lower(title)} 의 pg_trgm GIN 색인(V68)을 탄다.
 *
 * <p><b>게시글 관리</b>(docs/features/post-admin-contract.md): 모든 글 목록({@link #search}), 게시판 고정 수, 글별 열린 신고 사건의 신고 수·토론 열림.
 * 상태 = DELETED(지움·예약 삭제 시각 지남·임시저장) · HIDDEN(지우지 않았고 숨김 중) · PUBLIC(그 밖) — {@link AdminPostListRow#stateAt} 와 같은 기준.
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

    // ── 게시글 관리 ─────────────────────────────────────────────────────────

    /** 한 쪽 결과 */
    public record ListPage(List<AdminPostListRow> items, long total) {
    }


    private static ConstructorExpression<AdminPostListRow> listRow() {
        return Projections.constructor(AdminPostListRow.class, POST.id, POST.boardId, POST.prefixId, POST.authorId, POST.guestName, POST.title, POST.status,
                POST.adult, POST.viewCount, POST.likeCount, POST.commentCount, POST.pinnedAt, POST.hiddenAt, POST.hiddenReason, POST.hiddenUntil,
                POST.deleteScheduledAt, POST.deletedAt, POST.purgedAt, POST.createdAt);
    }

    /** 지운 글 — 지움 · 공개 아님(임시저장) · 예약 삭제 시각 지남 */
    private static BooleanExpression gone(Instant now) {
        return POST.deletedAt.isNotNull().or(POST.status.ne(PostEntity.PUBLISHED)).or(POST.deleteScheduledAt.isNotNull().and(POST.deleteScheduledAt.loe(now)));
    }

    /** 숨김 중(임시조치는 기간 안) — 지움 여부는 보지 않는다 */
    private static BooleanExpression hiddenNow(Instant now) {
        return POST.hiddenAt.isNotNull().and(POST.hiddenUntil.isNull().or(POST.hiddenUntil.gt(now)));
    }

    private static BooleanExpression stateIs(String state, Instant now) {
        BooleanExpression alive = PostRepositoryQDSL.alive(POST, now);
        return switch (state) {
            case AdminPostListRow.DELETED -> gone(now);
            case AdminPostListRow.HIDDEN -> alive.and(hiddenNow(now));
            default -> alive.and(PostRepositoryQDSL.notHidden(POST, now));
        };
    }

    /** 작성 시각 ↔ 글 ID(Snowflake) 범위의 여유 — 발급 시각과 {@code created_at} 의 작은 어긋남을 덮는다. 정확한 경계는 {@code created_at} 조건이 정한다 */
    private static final Duration ID_RANGE_SLACK = Duration.ofHours(1);

    /**
     * 상태를 뺀 거르기 — 글 번호 · 제목(대소문자 무시, pg_trgm 색인) · 작성자(회원 닉네임 · 비회원 이름) · 작성 기간 · 게시판 · 말머리. 본문은 찾지 않는다.
     * 작성 기간은 {@code created_at} 조건을 그대로 두고, id 범위(여유 포함)를 함께 걸어 월 조각 가지치기와 id 색인을 탄다.
     */
    private static BooleanBuilder filter(AdminPostSearch c) {
        BooleanBuilder where = new BooleanBuilder();
        if (c.postId() != null) {
            where = where.and(POST.id.eq(c.postId()));
        }
        if (c.title() != null) {
            where = where.and(POST.title.containsIgnoreCase(c.title()));
        }
        if (c.author() != null) {
            QProfileEntity profile = QProfileEntity.profileEntity;
            where = where.and(POST.authorId.isNull().and(POST.guestName.containsIgnoreCase(c.author()))
                    .or(POST.authorId.in(JPAExpressions.select(profile.userId).from(profile).where(profile.nickname.containsIgnoreCase(c.author())))));
        }
        if (c.createdFrom() != null) {
            where = where.and(POST.id.goe(SnowflakeIdGenerator.floorIdAt(c.createdFrom().minus(ID_RANGE_SLACK)))).and(POST.createdAt.goe(c.createdFrom()));
        }
        if (c.createdTo() != null) {
            where = where.and(POST.id.lt(SnowflakeIdGenerator.floorIdAt(c.createdTo().plus(ID_RANGE_SLACK)))).and(POST.createdAt.loe(c.createdTo()));
        }
        if (c.boardId() != null) {
            where = where.and(POST.boardId.eq(c.boardId()));
        }
        if (c.prefixId() != null) {
            where = where.and(POST.prefixId.eq(c.prefixId()));
        }
        return where;
    }

    /** 목록 한 쪽 — 정렬은 id(= 작성 시각) 순 */
    public ListPage search(AdminPostSearch c, long offset, int limit, Instant now) {
        if (c.none()) {
            return new ListPage(List.of(), 0);
        }
        BooleanBuilder where = filter(c);
        if (c.state() != null) {
            where = where.and(stateIs(c.state(), now));
        }
        Long total = query.select(POST.id.count()).from(POST).where(where).fetchOne();
        if (total == null || total == 0 || offset >= total) {
            return new ListPage(List.of(), total == null ? 0 : total);
        }
        List<AdminPostListRow> items = query.select(listRow()).from(POST).where(where)
                .orderBy(c.oldest() ? POST.id.asc() : POST.id.desc())
                .offset(offset).limit(limit)
                .fetch();
        return new ListPage(items, total);
    }

    /** 글 한 줄(상태와 무관) */
    public AdminPostListRow listRow(long id) {
        return query.select(listRow()).from(POST).where(POST.id.eq(id)).fetchOne();
    }

    /** 이 게시판에 지금 고정된(보이는) 글 수 — {@code exceptId} 는 빼고 센다 */
    public long pinnedCount(long boardId, long exceptId, Instant now) {
        Long count = query.select(POST.id.count()).from(POST)
                .where(POST.boardId.eq(boardId), POST.pinnedAt.isNotNull(), PostRepositoryQDSL.alive(POST, now), POST.id.ne(exceptId))
                .fetchOne();
        return count == null ? 0 : count;
    }

    /**
     * 글별 열린 신고 사건(처리 대기·검토 중)의 신고 수 — 그 글 자체의 사건만(댓글 사건은 세지 않는다). 게시글 관리 목록의 「신고」 칸(누르면 신고 관리 {@code /reports?postId=})
     */
    public Map<Long, Integer> openReports(Collection<Long> postIds) {
        Map<Long, Integer> out = new HashMap<>();
        if (postIds.isEmpty()) {
            return out;
        }
        QReportCaseEntity reportCase = QReportCaseEntity.reportCaseEntity;
        for (Tuple t : query.select(reportCase.targetId, reportCase.reportCount).from(reportCase)
                .where(reportCase.targetType.eq(ReportEntity.POST), reportCase.targetId.in(postIds), reportCase.status.in(ReportCaseEntity.OPEN))
                .fetch()) {
            Integer count = t.get(1, Integer.class);
            out.put(t.get(0, Long.class), count == null ? 0 : count);
        }
        return out;
    }

    /** 토론이 열린 글 */
    public Set<Long> debateOpen(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Set.of();
        }
        QPostDebateEntity debate = QPostDebateEntity.postDebateEntity;
        return new HashSet<>(query.select(debate.postId).from(debate).where(debate.postId.in(postIds)).fetch());
    }

    /** 글을 옮기면 그 글의 댓글 게시판 칸도 함께 옮긴다(댓글 표는 글 ID 해시 조각 — 한 조각만 고친다). 부르는 서비스의 트랜잭션 안에서 */
    public long moveComments(long postId, long boardId) {
        QCommentEntity comment = QCommentEntity.commentEntity;
        return query.update(comment).set(comment.boardId, boardId).where(comment.postId.eq(postId)).execute();
    }

    private static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
