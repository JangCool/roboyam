package com.community.group.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.exception.BoardNotFoundException;
import com.community.board.service.BoardAccessPolicy;
import com.community.board.service.PublicBoardService;
import com.community.group.dto.GroupRuleValues;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupManagerPermission;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupPublishRequestEntity;
import com.community.group.entity.GroupPublishRequestStatus;
import com.community.group.entity.GroupStatus;
import com.community.group.exception.GroupInactiveException;
import com.community.group.exception.GroupPostPrivateException;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.group.repository.master.GroupPostQueries;
import com.community.group.repository.master.GroupPublishRequestRepository;
import com.community.group.repository.master.GroupRepository;
import com.community.permission.entity.MemberPermission;
import com.community.post.dto.response.PostGroupView;
import com.community.post.entity.PostEntity;
import com.community.post.exception.PostNotFoundException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 글 API(상세·고치기·지우기·추천·댓글·투표)가 소모임 글을 열 때의 판정 한 곳 — community api-groups.md 8절 「누가 읽을 수 있나」·11-7.
 *
 * <ul>
 *   <li>일반 글: 지금까지처럼 공개 게시판 판정({@link PublicBoardService#requireActive(BoardCatalogSnapshot, long)} — 소모임 게시판이면 404).</li>
 *   <li>소모임 글: 작성자가 나가서 가린 글 404 {@code POST_NOT_FOUND} → 소모임 상태(열림만 — 비활성 403 {@code GROUP_INACTIVE}, 해산은 공개 글만 읽힌다, 그 밖 404)
 *       → 비멤버에게 보이지 않는 글({@code is_public} false — 비공개 소모임의 비공개 글 · 공개 소모임에서 멤버 전용으로 돌린 글)은 멤버(모임장 포함)만
 *       (아니면 비회원 포함 403 {@code GROUP_POST_PRIVATE}). 소모임 게시판이 꺼졌어도 막지 않는다(소모임은 멈추지 않는다 — 원본과 같다).</li>
 *   <li>소모임 글·댓글은 게시판 정책(쓰기·댓글 최소 등급·읽기 전용 잠금·운영진만)을 받지 않는다 — 계정 자격만 본다({@link #writeBlocked}·{@link #commentBlocked}).</li>
 *   <li>글 상세의 {@code group} 칸({@link #info}) — 공개 범위·공개 요청 상태·보는 사람이 할 수 있는 일. <b>단추를 보이는 근거일 뿐</b>이고 쓰기 요청 때
 *       {@code GroupPostVisibilityService} 가 다시 판정한다.</li>
 * </ul>
 * 지운 글·숨긴 글 판정은 부르는 쪽이 지금처럼 한다(지움 404 → 이 판정 → 숨김 403 {@code POST_HIDDEN}).
 */
@Component
public class GroupPostGate {

    static final String BY_GROUP = "GROUP";
    static final String BY_APPROVED = "APPROVED";
    static final String BY_DIRECT = "DIRECT";

    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupPublishRequestRepository requests;
    private final GroupPostQueries groupPosts;
    private final GroupVisibilityPolicy policy;

    public GroupPostGate(GroupRepository groups, GroupMemberRepository members, GroupPublishRequestRepository requests, GroupPostQueries groupPosts,
                         GroupVisibilityPolicy policy) {
        this.groups = groups;
        this.members = members;
        this.requests = requests;
        this.groupPosts = groupPosts;
        this.policy = policy;
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

    /**
     * 글 상세의 {@code group} — 공개 범위·공개 요청·권한 칸까지(일반 글이면 null). 공개 범위 기능이 꺼져 있으면 {@code canPublish} 는 옛 뜻(작성자의 직접 공개)이다.
     *
     * @param actor 보는 사람(비회원 null)
     */
    public PostGroupView info(Resolved r, PostEntity post, BoardActor actor) {
        if (!r.groupPost()) {
            return null;
        }
        GroupEntity g = r.group();
        boolean dissolved = g.getStatus() == GroupStatus.DELETED;
        boolean open = g.getStatus() == GroupStatus.ACTIVE;
        Long viewer = r.viewerId();
        boolean author = viewer != null && viewer.equals(post.getAuthorId());
        boolean writable = actor != null && !actor.guest() && GroupAccess.writeBlocked(actor) == null;
        boolean member = open && r.member() && writable; // 지금 멤버이고 쓸 수 있는 상태(해산·비활성은 아무것도 못 한다)
        boolean handler = viewer != null && GroupAccess.can(g, r.membership(), viewer, GroupManagerPermission.UNPUBLISH);
        boolean publicGroup = g.isPublicGroup();
        boolean visible = post.isPublicPost();
        boolean membersOnly = publicGroup && post.getMembersOnlyAt() != null;
        GroupRuleValues.Visibility rules = policy.rules();
        boolean enabled = rules.visibilityEnabled();
        Instant now = Instant.now();

        GroupPublishRequestEntity request = !publicGroup && (author || handler || visible) ? requests.findById(post.getId()).orElse(null) : null;
        String publicBy = !visible ? null : publicGroup ? BY_GROUP
                : request != null && request.getStatus() == GroupPublishRequestStatus.APPROVED ? BY_APPROVED : BY_DIRECT;

        boolean canPublish;
        boolean canRequest = false;
        boolean canDecide = false;
        if (enabled) {
            canPublish = member && handler && !visible && !publicGroup;
            boolean requestOpen = request == null || switch (request.getStatus()) {
                case PENDING -> false;
                case REJECTED -> request.getRejectCount() < rules.rejectLimit() && !retryLater(request, rules, now);
                default -> true;
            };
            canRequest = member && author && !handler && !visible && !publicGroup && requestOpen;
            canDecide = member && handler && !visible && !publicGroup && request != null && request.pending();
        } else {
            canPublish = member && author && !visible && !publicGroup; // 옛 뜻 — 작성자가 직접 공개한다
        }
        // 되돌리기 — 작성자는 강퇴됐어도·정지 중이어도 자기 글을 되돌린다(2단계 그대로), 모임장·매니저는 지금 멤버이고 쓸 수 있을 때
        boolean canUnpublish = open && visible && !publicGroup && (author || (member && handler));
        boolean canMakeMembersOnly = member && handler && publicGroup && visible;
        boolean canReleaseMembersOnly = member && handler && membersOnly;

        PostGroupView.PublishRequest info = null;
        if (enabled && request != null && !visible && (author || handler)
                && (request.getStatus() == GroupPublishRequestStatus.PENDING || request.getStatus() == GroupPublishRequestStatus.REJECTED)) {
            boolean rejected = request.getStatus() == GroupPublishRequestStatus.REJECTED;
            Instant canRetryAt = rejected && request.getRejectCount() < rules.rejectLimit() ? retryAt(request, rules) : null;
            info = new PostGroupView.PublishRequest(request.getStatus().name(), request.getRequestedAt(), request.getNote(), request.getRejectReason(),
                    rejected ? request.getDecidedAt() : null, canRetryAt, request.getRejectCount());
        }
        Integer membersOnlyComments = null;
        if (!r.member()) {
            membersOnlyComments = !publicGroup && post.getPublicGrantedAt() != null ? groupPosts.countCommentsBefore(post.getId(), post.getPublicGrantedAt(), now) : 0;
        }
        return new PostGroupView(Long.toString(g.getId()), g.getName(), visible ? PostGroupView.PUBLIC : PostGroupView.PRIVATE, dissolved, canPublish, canUnpublish,
                GroupVisibilityPolicy.exposed(g, enabled), publicBy, membersOnly, info, canRequest, canDecide, canMakeMembersOnly, canReleaseMembersOnly,
                membersOnlyComments, rules.textMax());
    }

    /**
     * 비멤버에게 공개 시각 이전 댓글을 가릴 기준 시각 — 비멤버·비회원이 보는 비공개 소모임의 개별 공개 글이면 {@code public_granted_at}, 그 밖 null(가리지 않는다).
     * 댓글 목록의 {@code hiddenBy MEMBERS_ONLY} 판정(api-groups.md 11-7)이 쓴다.
     */
    public static Instant membersOnlyBefore(Resolved r, PostEntity post) {
        if (!r.groupPost() || r.member() || r.group().isPublicGroup()) {
            return null;
        }
        return post.getPublicGrantedAt();
    }

    /** 거절된 요청을 다시 보낼 수 있는 시각(거절 시각 + 대기 시간) — 거절된 요청이 아니면 null */
    static Instant retryAt(GroupPublishRequestEntity request, GroupRuleValues.Visibility rules) {
        if (request.getStatus() != GroupPublishRequestStatus.REJECTED || request.getDecidedAt() == null) {
            return null;
        }
        return request.getDecidedAt().plus(Duration.ofHours(rules.retryHours()));
    }

    private static boolean retryLater(GroupPublishRequestEntity request, GroupRuleValues.Visibility rules, Instant now) {
        Instant at = retryAt(request, rules);
        return at != null && at.isAfter(now);
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
