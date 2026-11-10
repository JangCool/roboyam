package com.community.post.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.service.BoardActorResolver;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupManagerPermission;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.group.repository.master.GroupRepository;
import com.community.group.service.GroupAccess;
import com.community.operator.service.OperatorMemberAccountService;
import com.community.post.entity.PostEntity;
import com.community.post.entity.PostProcessStatusHistoryEntity;
import com.community.post.exception.SecretPostException;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 비밀글 판정 · 글 운영진 판정 한 곳(V166 운영 게시판 설정 {@code secretMode} · V168 비회원 비밀글·소모임 게시판).
 *
 * <p><b>비밀글을 읽을 수 있는 사람</b>
 * <ul>
 *   <li>일반 게시판 글: 글쓴이(회원) · 그 게시판의 매니저·스텝 · 운영자 회원 계정({@link BoardActorResolver} 가 운영자 회원 계정을 모든 일반 게시판의 운영진으로 넣는다 —
 *       게시판 응답 {@code viewer.staff} 와 같은 판정).</li>
 *   <li>소모임 글: 글쓴이 · 그 소모임의 소모임장·글 관리 매니저(맡은 일 {@code UNPUBLISH} — 소모임 글 공개 전환·되돌리기와 같은 판정, {@link GroupAccess#can}) · 운영자 회원 계정
 *       (정지 중이 아님).</li>
 *   <li>비회원 글: 위에 더해 비밀번호로 연 방문자({@link SecretPostUnlocks} — 1시간).</li>
 *   <li>관리자 앱 운영자는 관리자 API 로 늘 본다.</li>
 * </ul>
 * 글 하나를 여는 공개 API 는 403 {@code SECRET_POST}(대부분 {@code GroupPostGate#resolve} 가 부른다), 같은 게시판·소모임 목록은 줄을 남기고 내용 칸을 비우며,
 * 섞인 목록은 질의에서 뺀다({@code PostRepositoryQDSL#feedVisible}). 비밀글이 아니면 회원 판정을 읽지 않는다(요청당 추가 질의 없음).
 *
 * <p><b>처리 상태를 바꾸는 사람</b>({@link #statusChanger}) — 정지 중이 아닌 운영자 회원 계정({@code OPERATOR}) · 일반 게시판의 매니저·스텝({@code BOARD_STAFF}) ·
 * 소모임 글이면 그 소모임의 소모임장·글 관리 매니저({@code GROUP_STAFF}).
 */
@Component
public class SecretPostGate {

    private final BoardActorResolver actors;
    private final OperatorMemberAccountService operatorAccounts;
    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final SecretPostUnlocks unlocks;
    private final SecretPostReaders readers;

    public SecretPostGate(BoardActorResolver actors, OperatorMemberAccountService operatorAccounts, GroupRepository groups, GroupMemberRepository members,
                          SecretPostUnlocks unlocks, SecretPostReaders readers) {
        this.readers = readers;
        this.actors = actors;
        this.operatorAccounts = operatorAccounts;
        this.groups = groups;
        this.members = members;
        this.unlocks = unlocks;
    }

    /** 이 사람이 이 글을 읽을 수 있는가 — 비밀글이 아니면 늘 true(소모임 글이면 소모임·멤버 행을 읽는다) */
    public boolean canRead(BoardCatalogSnapshot s, PostEntity post, Long viewerId) {
        if (!post.isSecret()) {
            return true;
        }
        if (!post.isGroupPost()) {
            return readable(s, post, viewerId, null, null);
        }
        GroupEntity group = groups.findById(post.getGroupId()).orElse(null);
        GroupMemberEntity row = group == null || viewerId == null ? null
                : members.findByGroupIdAndUserId(group.getId(), viewerId).filter(GroupMemberEntity::active).orElse(null);
        return readable(s, post, viewerId, group, row);
    }

    /** 읽을 수 없는 비밀글이면 403 {@code SECRET_POST} */
    public void requireReadable(BoardCatalogSnapshot s, PostEntity post, Long viewerId) {
        if (!canRead(s, post, viewerId)) {
            throw denied(s, post);
        }
    }

    /**
     * {@link #requireReadable(BoardCatalogSnapshot, PostEntity, Long)} — 이미 읽은 소모임·멤버 행으로({@code GroupPostGate#resolve}). 일반 글이면 둘 다 null.
     *
     * @param membership 보는 사람의 지금 멤버 행(없으면 null)
     */
    public void requireReadable(BoardCatalogSnapshot s, PostEntity post, Long viewerId, GroupEntity group, GroupMemberEntity membership) {
        if (post.isSecret() && !readable(s, post, viewerId, group, membership)) {
            throw denied(s, post);
        }
    }

    private boolean readable(BoardCatalogSnapshot s, PostEntity post, Long viewerId, GroupEntity group, GroupMemberEntity membership) {
        if (!post.isSecret()) {
            return true;
        }
        if (post.isGuest() && unlocks.has(post.getId(), Instant.now())) {
            return true; // 비회원 비밀글 — 비밀번호로 연 방문자(V168)
        }
        return readers.memberCanRead(s, post, viewerId, group, membership); // 회원 판정은 shared 한 곳(AI 회원 쓰기와 같은 코드)
    }

    private SecretPostException denied(BoardCatalogSnapshot s, PostEntity post) {
        String slug = post.getBoardId() == null ? null : s.board(post.getBoardId()).map(b -> b.slug()).orElse(null);
        return new SecretPostException(slug, post.isGuest(), post.getGroupId());
    }

    /** 이미 만든 판정({@link BoardActor})으로 — 일반 게시판 목록처럼 여러 줄을 볼 때(비밀번호 열람은 {@link #unlocked} 로 따로) */
    public static boolean canRead(boolean secret, Long authorId, long boardId, BoardActor actor) {
        if (!secret) {
            return true;
        }
        if (actor == null || actor.guest()) {
            return false;
        }
        return actor.userId().equals(authorId) || actor.staffOf(boardId);
    }

    /** 이 방문자가 비밀번호로 연 비회원 비밀글인가(목록의 줄 가림) */
    public boolean unlocked(long postId) {
        return unlocks.has(postId, Instant.now());
    }

    /**
     * 소모임 목록에서 보는 사람이 그 소모임의 모든 비밀글을 읽는가 — 소모임장·글 관리 매니저 · 운영자 회원 계정(정지 중 아님). 글쓴이 본인 글은 부르는 쪽이 따로 본다.
     */
    public boolean groupStaffReader(BoardCatalogSnapshot s, GroupEntity group, Long viewerId) {
        if (viewerId == null) {
            return false;
        }
        GroupMemberEntity row = members.findByGroupIdAndUserId(group.getId(), viewerId).filter(GroupMemberEntity::active).orElse(null);
        return GroupAccess.can(group, row, viewerId, GroupManagerPermission.UNPUBLISH) || operatorAccount(s, viewerId);
    }

    /**
     * 이 회원이 이 글의 처리 상태를 바꿀 수 있으면 바꾼 쪽 구분, 아니면 null — 정지 중이면 null. 상태를 쓰는 게시판인지는 보지 않는다(부르는 쪽).
     *
     * @return {@link PostProcessStatusHistoryEntity#OPERATOR} · {@link PostProcessStatusHistoryEntity#BOARD_STAFF} · {@link PostProcessStatusHistoryEntity#GROUP_STAFF} · null
     */
    public String statusChanger(BoardCatalogSnapshot s, PostEntity post, Long userId) {
        if (userId == null) {
            return null;
        }
        BoardActor actor = actors.resolve(userId, s);
        if (actor.guest() || actor.restricted()) {
            return null;
        }
        if (operatorAccounts.isActiveOperatorAccount(userId)) {
            return PostProcessStatusHistoryEntity.OPERATOR;
        }
        if (post.isGroupPost()) {
            GroupEntity group = groups.findById(post.getGroupId()).orElse(null);
            GroupMemberEntity row = group == null ? null : members.findByGroupIdAndUserId(group.getId(), userId).filter(GroupMemberEntity::active).orElse(null);
            return group != null && GroupAccess.can(group, row, userId, GroupManagerPermission.UNPUBLISH) ? PostProcessStatusHistoryEntity.GROUP_STAFF : null;
        }
        return post.getBoardId() != null && actor.staffOf(post.getBoardId()) ? PostProcessStatusHistoryEntity.BOARD_STAFF : null;
    }

    /** 힘이 살아 있는 운영자 회원 계정이고 정지 중이 아닌가 */
    private boolean operatorAccount(BoardCatalogSnapshot s, long userId) {
        return readers.operatorAccount(s, userId);
    }
}
