package com.community.group.repository.master;

import com.community.group.entity.GroupManagerPermission;
import com.community.group.entity.GroupMemberStatus;
import com.community.group.entity.GroupPublishRequestEntity;
import com.community.group.entity.GroupPublishRequestStatus;
import com.community.group.entity.GroupRole;
import com.community.group.entity.GroupStatus;
import com.community.group.entity.QGroupEntity;
import com.community.group.entity.QGroupMemberEntity;
import com.community.group.entity.QGroupPublishRequestEntity;
import com.community.post.entity.QPostEntity;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 공개 요청 질의(V94 {@code group_post_publish_requests}, community api-groups.md 11-5) — 처리 대기 목록 · 대기 수 · 하루 요청 수 · 일괄 닫기 · 받는 사람.
 * <b>처리 대기 목록·대기 수는 글이 지워졌거나 가려진(운영자·신고 숨김 · 작성자가 나가서 가림) 요청을 세지 않는다</b> — 글 삭제 경로마다 요청을 닫는 고리를 걸지 않고 읽을 때 거른다.
 * 트랜잭션은 부르는 서비스의 것을 따르고, 쓰기는 소모임 행을 잠근 뒤 한다.
 */
@Repository
public class GroupPublishRequestQueries extends RoboyamRepositoryQDSL {

    private static final QGroupPublishRequestEntity R = QGroupPublishRequestEntity.groupPublishRequestEntity;
    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final QGroupEntity GROUP = QGroupEntity.groupEntity;
    private static final QGroupMemberEntity MEMBER = QGroupMemberEntity.groupMemberEntity;

    private final JdbcTemplate jdbc;

    public GroupPublishRequestQueries(JPAQueryFactory query, DataSource dataSource) {
        super(query);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 처리 대기 목록의 한 줄 — 요청과 글의 필요한 칸 */
    public record PendingRow(GroupPublishRequestEntity request, String title, Instant postCreatedAt, Instant postEditedAt, Long authorId) {
    }

    /** 요청 글이 지금 보이는 글인가(지우지 않음 · 숨김 중 아님 · 나가서 가린 글 아님) */
    private static BooleanExpression postVisible(Instant now) {
        return R.postId.eq(POST.id).and(PostRepositoryQDSL.alive(POST, now)).and(PostRepositoryQDSL.notHidden(POST, now)).and(POST.groupLeftAt.isNull());
    }

    /** 이 소모임의 처리 대기 요청 수 — 지워졌거나 가려진 글의 요청은 뺀다 */
    public long pendingCount(long groupId, Instant now) {
        Long n = query.select(R.count()).from(R, POST)
                .where(R.groupId.eq(groupId), R.status.eq(GroupPublishRequestStatus.PENDING), postVisible(now)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 열린(ACTIVE) 소모임 전체의 처리 대기 요청 수 — 관리자 설정 공개 범위 구획 {@code usage.pendingRequestCount} */
    public long pendingCountOfActiveGroups(Instant now) {
        Long n = query.select(R.count()).from(R, POST, GROUP)
                .where(R.status.eq(GroupPublishRequestStatus.PENDING), postVisible(now), GROUP.id.eq(R.groupId), GROUP.status.eq(GroupStatus.ACTIVE)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 처리 대기 요청 — 오래된 요청이 위(요청 시각, 글 ID). {@code afterAt}·{@code afterPostId} 가 있으면 그 뒤부터 */
    public List<PendingRow> pending(long groupId, Instant afterAt, Long afterPostId, int limit, Instant now) {
        BooleanBuilder where = new BooleanBuilder(R.groupId.eq(groupId)).and(R.status.eq(GroupPublishRequestStatus.PENDING)).and(postVisible(now));
        if (afterAt != null && afterPostId != null) {
            where.and(R.requestedAt.gt(afterAt).or(R.requestedAt.eq(afterAt).and(R.postId.gt(afterPostId))));
        }
        List<PendingRow> rows = new ArrayList<>();
        for (Tuple t : query.select(R, POST.title, POST.createdAt, POST.editedAt, POST.authorId).from(R, POST).where(where)
                .orderBy(R.requestedAt.asc(), R.postId.asc()).limit(limit).fetch()) {
            rows.add(new PendingRow(t.get(R), t.get(POST.title), t.get(POST.createdAt), t.get(POST.editedAt), t.get(POST.authorId)));
        }
        return rows;
    }

    /** 요청자가 {@code since} 뒤에 보낸(다시 보낸 것 포함) 요청 수 — 하루 한도 */
    public long countSince(long requesterId, Instant since) {
        Long n = query.select(R.count()).from(R).where(R.requesterId.eq(requesterId), R.requestedAt.goe(since)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 가장 오래된 {@code since} 뒤 요청 시각 — 하루 한도의 {@code retryAfterSeconds}(없으면 null) */
    public Instant oldestSince(long requesterId, Instant since) {
        return query.select(R.requestedAt.min()).from(R).where(R.requesterId.eq(requesterId), R.requestedAt.goe(since)).fetchOne();
    }

    /** 소모임이 공개 소모임이 되면 대기 요청을 모두 닫는다(글이 이미 공개로 바뀌었다). 닫은 수 */
    public int cancelPendingOfGroup(long groupId, String reason) {
        return jdbc.update("UPDATE group_post_publish_requests SET status = 'CANCELED', cancel_reason = ? WHERE group_id = ? AND status = 'PENDING'", reason, groupId);
    }

    /** 작성자가 소모임을 스스로 나가면 그 사람의 대기 요청을 닫는다(다시 가입해도 되살리지 않는다). 닫은 수 */
    public int cancelPendingOfRequester(long groupId, long requesterId, String reason) {
        return jdbc.update("UPDATE group_post_publish_requests SET status = 'CANCELED', cancel_reason = ? WHERE group_id = ? AND requester_id = ? AND status = 'PENDING'",
                reason, groupId, requesterId);
    }

    /** 이 소모임에서 그 일을 맡은 지금 매니저의 회원 ID(임명이 이른 순) — 공개 요청 알림을 받는 사람(모임장은 부르는 쪽이 더한다) */
    public List<Long> managerIdsWith(long groupId, GroupManagerPermission permission) {
        List<Long> out = new ArrayList<>();
        for (Tuple t : query.select(MEMBER.userId, MEMBER.managerPerms).from(MEMBER)
                .where(MEMBER.groupId.eq(groupId), MEMBER.role.eq(GroupRole.MANAGER), MEMBER.status.eq(GroupMemberStatus.ACTIVE))
                .orderBy(MEMBER.roleChangedAt.asc(), MEMBER.userId.asc()).fetch()) {
            Integer perms = t.get(MEMBER.managerPerms);
            if (perms != null && (perms & permission.bit()) != 0) {
                out.add(t.get(MEMBER.userId));
            }
        }
        return out;
    }

    /** 안내 알림 대상 한 줄 — 매니저 · 소모임 · 모임장 · 맡은 일 비트 */
    public record ManagerRow(long userId, long groupId, String groupName, long leaderId, int perms) {
    }

    /** 열린 소모임의 지금 매니저 중 그 일을 맡은 사람(소모임·회원 순) — 공개 범위 기능을 켤 때의 안내 알림 */
    public List<ManagerRow> activeManagersWith(GroupManagerPermission permission) {
        List<ManagerRow> out = new ArrayList<>();
        for (Tuple t : query.select(MEMBER.userId, GROUP.id, GROUP.name, GROUP.leaderId, MEMBER.managerPerms).from(MEMBER).join(GROUP).on(GROUP.id.eq(MEMBER.groupId))
                .where(GROUP.status.eq(GroupStatus.ACTIVE), MEMBER.role.eq(GroupRole.MANAGER), MEMBER.status.eq(GroupMemberStatus.ACTIVE))
                .orderBy(GROUP.id.asc(), MEMBER.userId.asc()).fetch()) {
            Integer perms = t.get(MEMBER.managerPerms);
            int bits = perms == null ? 0 : perms;
            if ((bits & permission.bit()) != 0) {
                out.add(new ManagerRow(t.get(MEMBER.userId), t.get(GROUP.id), t.get(GROUP.name), t.get(GROUP.leaderId), bits));
            }
        }
        return out;
    }

    /** 안내 알림을 보낼 차례를 선점한다 — 아직 보내지 않았을 때만 지금 시각을 적고 true(서버가 몇 대든 하나만 보낸다, 껐다 켜도 다시 보내지 않는다) */
    public boolean claimManagerNotice(Instant now) {
        return jdbc.update("UPDATE group_settings SET visibility_notice_sent_at = ? WHERE id = 1 AND visibility_notice_sent_at IS NULL",
                java.sql.Timestamp.from(now)) > 0;
    }
}
