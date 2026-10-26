package com.community.group.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.exception.BoardNotFoundException;
import com.community.board.service.BoardAccessPolicy;
import com.community.board.service.PublicBoardService;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupManagerPermission;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupStatus;
import com.community.group.exception.GroupInactiveException;
import com.community.group.exception.GroupPostPrivateException;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.group.repository.master.GroupRepository;
import com.community.permission.entity.MemberPermission;
import com.community.post.dto.response.PostGroupView;
import com.community.post.entity.PostEntity;
import com.community.post.exception.PostNotFoundException;
import org.springframework.stereotype.Component;

/**
 * 글 API(상세·고치기·지우기·추천·댓글·투표)가 소모임 글을 열 때의 판정 한 곳 — community api-groups.md 8절 「누가 읽을 수 있나」.
 *
 * <ul>
 *   <li>일반 글: 지금까지처럼 공개 게시판 판정({@link PublicBoardService#requireActive(BoardCatalogSnapshot, long)} — 소모임 게시판이면 404).</li>
 *   <li>소모임 글: 작성자가 나가서 가린 글 404 {@code POST_NOT_FOUND} → 소모임 상태(열림만 — 비활성 403 {@code GROUP_INACTIVE}, 해산은 공개 글만 읽힌다, 그 밖 404)
 *       → 비공개 글은 멤버(모임장 포함)만(아니면 비회원 포함 403 {@code GROUP_POST_PRIVATE}). 소모임 게시판이 꺼졌어도 막지 않는다(소모임은 멈추지 않는다 — 원본과 같다).</li>
 *   <li>소모임 글·댓글은 게시판 정책(쓰기·댓글 최소 등급·읽기 전용 잠금·운영진만)을 받지 않는다 — 계정 자격만 본다({@link #writeBlocked}·{@link #commentBlocked}).</li>
 * </ul>
 * 지운 글·숨긴 글 판정은 부르는 쪽이 지금처럼 한다(지움 404 → 이 판정 → 숨김 403 {@code POST_HIDDEN}).
 */
@Component
public class GroupPostGate {

    private final GroupRepository groups;
    private final GroupMemberRepository members;

    public GroupPostGate(GroupRepository groups, GroupMemberRepository members) {
        this.groups = groups;
        this.members = members;
    }

    /**
     * 판정 결과 — 일반 글이면 {@code group}·{@code membership} 이 null.
     *
     * @param membership 보는 사람의 지금 멤버 행(멤버가 아니거나 비회원이면 null — 모임장도 멤버 행이 있다)
     */
    public record Resolved(EffectiveBoard board, GroupEntity group, GroupMemberEntity membership, Long viewerId) {

        public boolean groupPost() {
            return group != null;
        }

        /** 보는 사람이 지금 멤버(모임장 포함)인가 */
        public boolean member() {
            return group != null && viewerId != null && (group.getLeaderId().equals(viewerId) || membership != null);
        }
    }

    /**
     * 이 사람이 이 글을 열 수 있는가 — 열 수 있으면 글이 놓인 게시판과 소모임.
     *
     * @param viewerId 보는 회원(비회원 null)
     */
    public Resolved resolve(BoardCatalogSnapshot s, PostEntity post, Long viewerId) {
        if (!post.isGroupPost()) {
            return new Resolved(PublicBoardService.requireActive(s, post.getBoardId()), null, null, viewerId);
        }
        if (post.isGroupLeft()) {
            throw new PostNotFoundException(); // 작성자가 소모임을 나가 가려진 글 — 글 주소는 없는 글과 같다
        }
        GroupEntity group = groups.findById(post.getGroupId()).orElseThrow(PostNotFoundException::new);
        if (group.getStatus() == GroupStatus.INACTIVE) {
            throw new GroupInactiveException();
        }
        boolean dissolved = group.getStatus() == GroupStatus.DELETED;
        if (group.getStatus() != GroupStatus.ACTIVE && !(dissolved && post.isPublicPost())) {
            throw new PostNotFoundException();
        }
        GroupMemberEntity membership = viewerId == null || dissolved ? null
                : members.findByGroupIdAndUserId(group.getId(), viewerId).filter(GroupMemberEntity::active).orElse(null);
        Resolved resolved = new Resolved(s.board(post.getBoardId()).orElseThrow(BoardNotFoundException::new), group, membership, viewerId);
        if (!post.isPublicPost() && !resolved.member()) {
            throw new GroupPostPrivateException();
        }
        return resolved;
    }

    /** 글 상세의 {@code group} — 권한 칸까지(일반 글이면 null) */
    public static PostGroupView info(Resolved r, PostEntity post, BoardActor actor) {
        if (!r.groupPost()) {
            return null;
        }
        GroupEntity g = r.group();
        boolean dissolved = g.getStatus() == GroupStatus.DELETED;
        boolean open = g.getStatus() == GroupStatus.ACTIVE;
        Long viewer = r.viewerId();
        boolean author = viewer != null && viewer.equals(post.getAuthorId());
        boolean canPublish = open && author && r.member() && !post.isPublicPost() && actor != null && GroupAccess.writeBlocked(actor) == null;
        boolean canUnpublish = open && post.isPublicPost() && viewer != null
                && (author || GroupAccess.can(g, r.membership(), viewer, GroupManagerPermission.UNPUBLISH));
        return new PostGroupView(Long.toString(g.getId()), g.getName(), post.isPublicPost() ? PostGroupView.PUBLIC : PostGroupView.PRIVATE, dissolved, canPublish,
                canUnpublish);
    }

    /**
     * 소모임 글을 쓸 수 없는 까닭(쓸 수 있으면 null) — 1단계 쓰기 자격 차례(이메일 인증 → 정지 → 사업자 회원) + 회원 역할 권한 {@code post.write}({@code BOARD_RESTRICTED}).
     * 비회원은 {@code LOGIN_REQUIRED}. 게시판 정책(등급·잠금·운영진만)은 보지 않는다.
     */
    public static String writeBlocked(BoardActor actor) {
        return blocked(actor, MemberPermission.POST_WRITE.code());
    }

    /** 소모임 글에 댓글을 쓸 수 없는 까닭(쓸 수 있으면 null) — 글쓰기와 같은 차례 + {@code comment.write}. 비회원은 소모임 글에 댓글을 쓰지 않는다 */
    public static String commentBlocked(BoardActor actor) {
        return blocked(actor, MemberPermission.COMMENT_WRITE.code());
    }

    private static String blocked(BoardActor actor, String permission) {
        if (actor == null || actor.guest()) {
            return BoardAccessPolicy.LOGIN_REQUIRED;
        }
        String reason = GroupAccess.writeBlocked(actor);
        if (reason != null) {
            return reason;
        }
        return actor.can(permission) ? null : BoardAccessPolicy.BOARD_RESTRICTED;
    }
}
