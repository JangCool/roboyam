package com.community.group.repository.master;

import com.community.group.entity.QGroupMemberEntity;
import com.community.post.dto.PostRowData;
import com.community.post.entity.QPostEntity;
import com.community.post.entity.PostProcessStatus;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * 소모임 글(V91 — {@code posts.group_id}·{@code is_public}·{@code group_left_at}) 질의 — 글 탭 · 글 수 · 새 글 수 · 나가기·다시 가입·해산의 일괄 처리.
 * 색인은 {@code ix_posts_group (group_id, id DESC)} · {@code ix_posts_group_author (group_id, author_id)}.
 *
 * <p><b>보이는 소모임 글</b>({@link #visible}): 지우지 않음({@link PostRepositoryQDSL#alive}) · 운영자·신고 숨김 중이 아님({@link PostRepositoryQDSL#notHidden}) ·
 * 작성자가 나가서 가린 글이 아님. 글 탭·글 수·새 글 수가 모두 이 조건이다.
 *
 * <p>일괄 처리는 QueryDSL 대량 UPDATE — 한 사람·한 소모임의 글을 한 문장으로 바꾼다({@code group_left_at} 은 엔티티가 고치지 않는 칸이지만 대량 UPDATE 로는 바꾼다). 트랜잭션은 부르는 서비스의 것을 따른다
 * (소모임 행을 잠근 뒤 부른다 — 글쓰기·공개 전환과 줄을 선다).
 */
@Repository
public class GroupPostQueries extends RoboyamRepositoryQDSL {

    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final QGroupMemberEntity MEMBER = QGroupMemberEntity.groupMemberEntity;

    public GroupPostQueries(JPAQueryFactory query) {
        super(query);
    }

    /** 글 탭의 한 줄 — 목록 칸 + 공개 전환 여부 */
    public record Row(PostRowData data, boolean publicPost) {
    }

    /** 보이는 소모임 글 조건(그 소모임) */
    public static BooleanExpression visible(QPostEntity p, long groupId, Instant now) {
        return p.groupId.eq(groupId).and(PostRepositoryQDSL.alive(p, now)).and(PostRepositoryQDSL.notHidden(p, now)).and(p.groupLeftAt.isNull());
    }

    private static ConstructorExpression<PostRowData> row() {
        return Projections.constructor(PostRowData.class, POST.id, POST.boardId, POST.prefixId, POST.authorId, POST.guestName, POST.authorIp, POST.title,
                POST.hasImage, POST.adult, POST.viewCount, POST.likeCount, POST.commentCount, POST.pinnedAt, POST.hiddenAt, POST.hiddenUntil, POST.hiddenReason,
                POST.createdAt, POST.groupId, POST.secret, POST.processStatus, POST.anonymous);
    }

    /**
     * 글 탭 — 최신이 위(id 내림차순), 커서로 더보기.
     *
     * @param includePrivate 멤버면 true(비공개 글까지), 아니면 공개로 전환된 글만
     * @param beforeId       이 ID 보다 오래된 글만(커서) — null 이면 처음부터
     * @param limit          읽을 줄 수(다음 쪽이 있는지 보려면 쪽 크기 + 1)
     */
    public List<Row> rows(long groupId, boolean includePrivate, Long beforeId, int limit, Instant now) {
        return rows(groupId, includePrivate, null, beforeId, limit, now);
    }

    /**
     * {@link #rows(long, boolean, Long, int, Instant)} + 처리 상태 거르기(V168 — 소모임 게시판이 상태를 쓸 때, {@code RECEIVED} 는 값 없는 글도 — 게시판 목록과 같다)
     *
     * @param status 거를 상태(null 이면 전체)
     */
    public List<Row> rows(long groupId, boolean includePrivate, PostProcessStatus status, Long beforeId, int limit, Instant now) {
        BooleanExpression where = visible(POST, groupId, now).and(PostRepositoryQDSL.processStatusIs(POST, status));
        if (!includePrivate) {
            where = where.and(POST.publicPost.isTrue());
        }
        if (beforeId != null) {
            where = where.and(POST.id.lt(beforeId));
        }
        ConstructorExpression<PostRowData> row = row();
        List<Tuple> tuples = query.select(row, POST.publicPost).from(POST).where(where).orderBy(POST.id.desc()).limit(limit).fetch();
        return tuples.stream().map(t -> new Row(t.get(row), Boolean.TRUE.equals(t.get(POST.publicPost)))).toList();
    }

    /** 쪽 번호 목록의 한 쪽 — 줄 + 조건에 맞는 전체 수 */
    public record Paged(List<Row> rows, long total) {
    }

    /**
     * 글 탭 — 게시판 목록처럼 쪽 번호로(정렬·말머리·검색, 2026-11-06 사용자). 보이는 글 조건은 {@link #rows} 와 같다.
     *
     * @param includePrivate 멤버면 true(비공개 글까지), 아니면 공개로 전환된 글만
     * @param prefixId       말머리 — null 이면 전체
     * @param q              검색어(게시판 안 검색과 같다 — {@link PostRepositoryQDSL#search}) — null 이면 검색하지 않는다
     * @param oldest         오래된순(아니면 최신순)
     */
    public Paged page(long groupId, boolean includePrivate, Long prefixId, String q, boolean oldest, long offset, int limit, Instant now) {
        return page(groupId, includePrivate, prefixId, null, q, oldest, offset, limit, now);
    }

    /** {@link #page(long, boolean, Long, String, boolean, long, int, Instant)} + 처리 상태 거르기(V168 — null 이면 전체) */
    public Paged page(long groupId, boolean includePrivate, Long prefixId, PostProcessStatus status, String q, boolean oldest, long offset, int limit, Instant now) {
        BooleanExpression where = visible(POST, groupId, now).and(PostRepositoryQDSL.processStatusIs(POST, status));
        if (!includePrivate) {
            where = where.and(POST.publicPost.isTrue());
        }
        if (prefixId != null) {
            where = where.and(POST.prefixId.eq(prefixId));
        }
        if (q != null) {
            where = where.and(PostRepositoryQDSL.search(q, now));
        }
        Long total = query.select(POST.id.count()).from(POST).where(where).fetchOne();
        if (total == null || total == 0 || offset >= total) {
            return new Paged(List.of(), total == null ? 0 : total);
        }
        ConstructorExpression<PostRowData> row = row();
        List<Tuple> tuples = query.select(row, POST.publicPost).from(POST).where(where).orderBy(oldest ? POST.id.asc() : POST.id.desc()).offset(offset).limit(limit).fetch();
        return new Paged(tuples.stream().map(t -> new Row(t.get(row), Boolean.TRUE.equals(t.get(POST.publicPost)))).toList(), total);
    }

    /** 보이는 글 수 — {@code publicOnly} 면 공개로 전환된 글만 */
    public long count(long groupId, boolean publicOnly, Instant now) {
        BooleanExpression where = visible(POST, groupId, now);
        if (publicOnly) {
            where = where.and(POST.publicPost.isTrue());
        }
        Long n = query.select(POST.id.count()).from(POST).where(where).fetchOne();
        return n == null ? 0 : n;
    }

    /** 잠긴(비공개) 글 수 — 비멤버의 글 탭 「멤버에게만 보이는 글 N개」 */
    public long privateCount(long groupId, Instant now) {
        Long n = query.select(POST.id.count()).from(POST).where(visible(POST, groupId, now), POST.publicPost.isFalse()).fetchOne();
        return n == null ? 0 : n;
    }

    /**
     * 관리자 상세의 글 수 — {@code [0]} 지우지 않은 소모임 글 수(숨김·나가서 가린 글 포함), {@code [1]} 그중 비멤버에게 보이는 글 수(공개로 전환됐고 숨김·가림이 아님).
     */
    public long[] adminCounts(long groupId, Instant now) {
        Long all = query.select(POST.id.count()).from(POST).where(POST.groupId.eq(groupId), PostRepositoryQDSL.alive(POST, now)).fetchOne();
        return new long[] {all == null ? 0 : all, count(groupId, true, now)};
    }

    /**
     * 새 글 수 — 그 회원이 글 탭을 마지막으로 본 시각(본 적이 없으면 가입 시각) 뒤에 올라온 <b>남의</b> 보이는 글 수. 소모임 ID → 수(없으면 0 이라 빠진다).
     * 그 회원의 멤버 행과 한 번에 잇는다(소모임 수와 무관하게 질의 한 번).
     */
    public Map<Long, Long> newPostCounts(long userId, Collection<Long> groupIds, Instant now) {
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        List<Tuple> rows = query.select(POST.groupId, POST.id.count()).from(POST)
                .join(MEMBER).on(MEMBER.groupId.eq(POST.groupId).and(MEMBER.userId.eq(userId)))
                .where(POST.groupId.in(groupIds), PostRepositoryQDSL.alive(POST, now), PostRepositoryQDSL.notHidden(POST, now), POST.groupLeftAt.isNull(),
                        POST.authorId.isNull().or(POST.authorId.ne(userId)), POST.createdAt.gt(MEMBER.lastSeenAt.coalesce(MEMBER.joinedAt)))
                .groupBy(POST.groupId)
                .fetch();
        Map<Long, Long> out = new HashMap<>();
        rows.forEach(t -> out.put(t.get(POST.groupId), t.get(1, Long.class)));
        return out;
    }

    // ── 일괄 처리(QueryDSL 대량 UPDATE — 엔티티가 고치지 않는 group_left_at 도 대량 UPDATE 로는 바꿀 수 있다) ─────────

    /** 스스로 나갔다 — 그 소모임 안의 그 사람 글을 모두 가린다(공개 여부는 그대로). 가린 글 수 */
    public int hideForLeaver(long groupId, long authorId, Instant now) {
        return rows(query.update(POST).set(POST.groupLeftAt, now).set(POST.updatedAt, now)
                .where(POST.groupId.eq(groupId), POST.authorId.eq(authorId), POST.groupLeftAt.isNull()).execute());
    }

    /** 다시 가입했다 — 나가서 가린 글만 되살린다(운영자·신고 숨김은 칸이 달라 그대로 숨긴 채다). 되살린 글 수 */
    public int restoreForMember(long groupId, long authorId, Instant now) {
        return rows(query.update(POST).setNull(POST.groupLeftAt).set(POST.updatedAt, now)
                .where(POST.groupId.eq(groupId), POST.authorId.eq(authorId), POST.groupLeftAt.isNotNull()).execute());
    }

    // ── 공개 범위(5단계 — api-groups.md 11-1·11-4) ─────────────────────────────

    /**
     * 비공개→공개 일괄 열기 — 멤버 전용 기록이 없는 모든 글(지운·숨긴·나가서 가린 글 포함 — 되살아나는 글이 저장 규칙과 어긋나지 않게)을 유효 공개로. 개별 공개 기록은 그대로 둔다.
     * 바뀐 행 수
     */
    public int openAllExceptMembersOnly(long groupId, Instant now) {
        return rows(query.update(POST).set(POST.publicPost, true).set(POST.updatedAt, now)
                .where(POST.groupId.eq(groupId), POST.publicPost.isFalse(), POST.membersOnlyAt.isNull()).execute());
    }

    /** 공개→비공개 일괄 닫기 — 모든 글을 유효 비공개로 하고 개별 공개 기록을 지운다(멤버 전용 기록은 잠재워 둔다). 바뀐 행 수 */
    public int closeAll(long groupId, Instant now) {
        return rows(query.update(POST).set(POST.publicPost, false).setNull(POST.publicGrantedAt).setNull(POST.publicGrantedBy).set(POST.updatedAt, now)
                .where(POST.groupId.eq(groupId), POST.publicPost.isTrue().or(POST.publicGrantedAt.isNotNull())).execute());
    }

    /** 보이는 글 중 멤버 전용 기록이 없는 글 수 — 비공개→공개 뒤 비멤버에게 보이게 될 글(이미 개별 공개된 글도 공개 시각 이전 댓글이 열리므로 센다) */
    public int countOpenable(long groupId, Instant now) {
        return count(visible(POST, groupId, now).and(POST.membersOnlyAt.isNull()));
    }

    /** 위 글들의 지우지 않은 댓글 수 */
    public int countCommentsOfOpenable(long groupId, Instant now) {
        return comments(visible(POST, groupId, now).and(POST.membersOnlyAt.isNull()));
    }

    /** 보이는 글 중 지금 비멤버에게 보이는 글 수 — 공개→비공개로 닫힐 글 */
    public int countVisibleToAll(long groupId, Instant now) {
        return count(visible(POST, groupId, now).and(POST.publicPost.isTrue()));
    }

    /** 위 글들의 지우지 않은 댓글 수 */
    public int countCommentsOfVisibleToAll(long groupId, Instant now) {
        return comments(visible(POST, groupId, now).and(POST.publicPost.isTrue()));
    }

    /** 보이는 글 중 개별 공개돼 있는 글 수 */
    public int countIndividuallyPublic(long groupId, Instant now) {
        return count(visible(POST, groupId, now).and(POST.publicGrantedAt.isNotNull()));
    }

    /** 보이는 글 중 멤버 전용으로 돌려 둔 기록이 있는 글 수 */
    public int countMembersOnly(long groupId, Instant now) {
        return count(visible(POST, groupId, now).and(POST.membersOnlyAt.isNotNull()));
    }

    /**
     * 비공개 소모임의 개별 공개 글에서 공개 시각({@code before}) 이전에 달린, 지우지 않고 숨기지 않은 댓글 수 — 글 상세 {@code membersOnlyCommentCount}·댓글 목록
     * {@code membersOnlyCount}(api-groups.md 11-7)
     */
    public int countCommentsBefore(long postId, Instant before, Instant now) {
        com.community.comment.entity.QCommentEntity c = com.community.comment.entity.QCommentEntity.commentEntity;
        Long n = query.select(c.id.count()).from(c)
                .where(c.postId.eq(postId), c.createdAt.lt(before), c.deletedAt.isNull(), c.hiddenAt.isNull().or(c.hiddenUntil.isNotNull().and(c.hiddenUntil.loe(now))))
                .fetchOne();
        return n == null ? 0 : (int) Math.min(n, Integer.MAX_VALUE);
    }

    private int count(BooleanExpression where) {
        Long n = query.select(POST.id.count()).from(POST).where(where).fetchOne();
        return n == null ? 0 : (int) Math.min(n, Integer.MAX_VALUE);
    }

    /** 그 글들의 지우지 않은 댓글 수(글 ID 로 시작해 글의 파티션만 읽는다) */
    private int comments(BooleanExpression postWhere) {
        com.community.comment.entity.QCommentEntity c = com.community.comment.entity.QCommentEntity.commentEntity;
        Long n = query.select(c.id.count()).from(c)
                .where(c.deletedAt.isNull(), c.postId.in(com.querydsl.jpa.JPAExpressions.select(POST.id).from(POST).where(postWhere))).fetchOne();
        return n == null ? 0 : (int) Math.min(n, Integer.MAX_VALUE);
    }

    /**
     * 해산 — 그 소모임의 지우지 않은 글을 공개 여부와 상관없이 모두 지운다(2026-10-26 사용자 결정, V96 — 글 주소 404, 최신글·검색·인기글·프로필에서 빠진다).
     * 일반 글 삭제({@code PostEntity#delete})와 같은 뜻이라 원문·투표·공개 요청 행은 그대로 두고(읽는 쪽이 지운 글을 거른다), 포인트는 회수하지 않는다. 지운 글 수
     */
    public int deleteOnDissolve(long groupId, Instant now) {
        return rows(query.update(POST).set(POST.deletedAt, now).setNull(POST.pinnedAt).set(POST.updatedAt, now)
                .where(POST.groupId.eq(groupId), POST.deletedAt.isNull()).execute());
    }

    private static int rows(long changed) {
        return Math.toIntExact(changed);
    }
}
