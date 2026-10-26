package com.community.group.repository.master;

import com.community.group.entity.QGroupMemberEntity;
import com.community.post.dto.PostRowData;
import com.community.post.entity.QPostEntity;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 소모임 글(V90 — {@code posts.group_id}·{@code is_public}·{@code group_left_at}) 질의 — 글 탭 · 글 수 · 새 글 수 · 나가기·다시 가입·해산의 일괄 처리.
 * 색인은 {@code ix_posts_group (group_id, id DESC)} · {@code ix_posts_group_author (group_id, author_id)}.
 *
 * <p><b>보이는 소모임 글</b>({@link #visible}): 지우지 않음({@link PostRepositoryQDSL#alive}) · 운영자·신고 숨김 중이 아님({@link PostRepositoryQDSL#notHidden}) ·
 * 작성자가 나가서 가린 글이 아님. 글 탭·글 수·새 글 수가 모두 이 조건이다.
 *
 * <p>일괄 처리는 native SQL — {@code group_left_at} 은 엔티티가 고치지 않는 칸이고, 한 사람·한 소모임의 글을 한 문장으로 바꾼다. 트랜잭션은 부르는 서비스의 것을 따른다
 * (소모임 행을 잠근 뒤 부른다 — 글쓰기·공개 전환과 줄을 선다).
 */
@Repository
public class GroupPostQueries extends RoboyamRepositoryQDSL {

    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final QGroupMemberEntity MEMBER = QGroupMemberEntity.groupMemberEntity;

    private final JdbcTemplate jdbc;

    public GroupPostQueries(JPAQueryFactory query, DataSource dataSource) {
        super(query);
        this.jdbc = new JdbcTemplate(dataSource);
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
                POST.createdAt);
    }

    /**
     * 글 탭 — 최신이 위(id 내림차순), 커서로 더보기.
     *
     * @param includePrivate 멤버면 true(비공개 글까지), 아니면 공개로 전환된 글만
     * @param beforeId       이 ID 보다 오래된 글만(커서) — null 이면 처음부터
     * @param limit          읽을 줄 수(다음 쪽이 있는지 보려면 쪽 크기 + 1)
     */
    public List<Row> rows(long groupId, boolean includePrivate, Long beforeId, int limit, Instant now) {
        BooleanExpression where = visible(POST, groupId, now);
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

    // ── 일괄 처리(native) ────────────────────────────────────────────────────

    /** 스스로 나갔다 — 그 소모임 안의 그 사람 글을 모두 가린다(공개 여부는 그대로). 가린 글 수 */
    public int hideForLeaver(long groupId, long authorId, Instant now) {
        return jdbc.update("UPDATE posts SET group_left_at = ?, updated_at = ? WHERE group_id = ? AND author_id = ? AND group_left_at IS NULL",
                Timestamp.from(now), Timestamp.from(now), groupId, authorId);
    }

    /** 다시 가입했다 — 나가서 가린 글만 되살린다(운영자·신고 숨김은 칸이 달라 그대로 숨긴 채다). 되살린 글 수 */
    public int restoreForMember(long groupId, long authorId, Instant now) {
        return jdbc.update("UPDATE posts SET group_left_at = NULL, updated_at = ? WHERE group_id = ? AND author_id = ? AND group_left_at IS NOT NULL",
                Timestamp.from(now), groupId, authorId);
    }

    /** 해산 — 비공개 글과 나가서 가린 글을 지운다(공개로 전환돼 보이는 글은 연결을 유지한 채 남는다). 지운 글 수 */
    public int deleteOnDissolve(long groupId, Instant now) {
        return jdbc.update("UPDATE posts SET deleted_at = ?, pinned_at = NULL, updated_at = ? WHERE group_id = ? AND deleted_at IS NULL "
                + "AND (NOT is_public OR group_left_at IS NOT NULL)", Timestamp.from(now), Timestamp.from(now), groupId);
    }
}
