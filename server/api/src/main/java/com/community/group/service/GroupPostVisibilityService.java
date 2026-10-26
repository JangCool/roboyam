package com.community.group.service;

import com.community.board.exception.NotAuthorException;
import com.community.group.dto.GroupRuleValues;
import com.community.group.dto.request.GroupPostVisibilityRequest;
import com.community.group.dto.request.GroupVisibilityRequests;
import com.community.group.dto.response.GroupPostVisibilityView;
import com.community.group.dto.response.GroupVisibilityViews;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupManagerPermission;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupMemberStatus;
import com.community.group.entity.GroupPublishRequestEntity;
import com.community.group.entity.GroupPublishRequestStatus;
import com.community.group.entity.GroupRole;
import com.community.group.entity.GroupVisibility;
import com.community.group.entity.GroupVisibilityChangeEntity;
import com.community.group.exception.GroupForbiddenException;
import com.community.group.exception.GroupManagerPermissionDeniedException;
import com.community.group.exception.GroupNotMemberException;
import com.community.group.exception.GroupPostAlreadyPublicException;
import com.community.group.exception.GroupPostMembersOnlyException;
import com.community.group.exception.GroupPostPublicByGroupException;
import com.community.group.exception.GroupPublishRateLimitedException;
import com.community.group.exception.GroupPublishRequestDecidedException;
import com.community.group.exception.GroupPublishRequestExistsException;
import com.community.group.exception.GroupPublishRequestLimitException;
import com.community.group.exception.GroupPublishRequestNotFoundException;
import com.community.group.exception.GroupPublishRequestNotNeededException;
import com.community.group.exception.GroupPublishRequestRequiredException;
import com.community.group.exception.GroupPublishRequestRetryLaterException;
import com.community.group.exception.GroupVisibilityCooldownException;
import com.community.group.exception.GroupVisibilityDisabledException;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.group.repository.master.GroupPostQueries;
import com.community.group.repository.master.GroupPublishRequestQueries;
import com.community.group.repository.master.GroupPublishRequestRepository;
import com.community.group.repository.master.GroupVisibilityChangeRepository;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.post.entity.PostEntity;
import com.community.post.exception.PostNotFoundException;
import com.community.post.repository.master.PostRepository;
import com.community.post.service.PostQueryService;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소모임 공개 범위와 글 공개의 <b>쓰는 길 전부</b>(community api-groups.md 11절 — 5단계) — 글 단위 직접 공개·되돌리기·멤버 전용으로 돌리기·다시 공개(11-6),
 * 공개 요청·취소·목록·승인·거절(11-5), 소모임 공개 범위 미리보기·변경(11-4). 쓰기는 모두 <b>소모임 행을 잠그고</b> 같은 트랜잭션에서 한다 — 글쓰기
 * ({@link GroupPostService#create})와 줄을 서므로 저장된 유효 노출({@code posts.is_public})이 {@code (소모임 PUBLIC AND members_only_at IS NULL) OR public_granted_at IS NOT NULL}
 * 과 어긋나지 않는다. 읽는 쪽은 {@code is_public} 한 칸만 읽으므로 이 클래스만 저장 규칙을 안다.
 *
 * <p>권한은 {@link GroupAccess}(모임장 · 「글 공개 처리·되돌리기」({@code UNPUBLISH}) 매니저)가 요청마다 DB 로 판정한다. 공개 범위 기능({@link GroupVisibilityPolicy#enabled()})이 꺼져 있으면
 * 비공개 소모임의 공개 전환은 2단계 동작(작성자만)이고, 요청·처리·비공개→공개 변경은 403 {@code GROUP_VISIBILITY_DISABLED} 다(공개→비공개 · 멤버 전용 돌리기·다시 공개는 열려 있다).
 */
@Service
public class GroupPostVisibilityService {

    private static final Logger log = LoggerFactory.getLogger(GroupPostVisibilityService.class);

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;
    static final String ROLE_LEADER = "LEADER";
    static final String ROLE_MANAGER = "MANAGER";

    private final GroupAccess access;
    private final GroupMemberRepository members;
    private final PostRepository posts;
    private final GroupPublishRequestRepository requests;
    private final GroupPublishRequestQueries requestQueries;
    private final GroupVisibilityChangeRepository changes;
    private final GroupPostQueries groupPosts;
    private final GroupVisibilityPolicy policy;
    private final GroupPeople people;
    private final GroupNotices notices;
    private final SnowflakeIdGenerator ids;

    public GroupPostVisibilityService(GroupAccess access, GroupMemberRepository members, PostRepository posts, GroupPublishRequestRepository requests,
                                      GroupPublishRequestQueries requestQueries, GroupVisibilityChangeRepository changes, GroupPostQueries groupPosts,
                                      GroupVisibilityPolicy policy, GroupPeople people, GroupNotices notices, SnowflakeIdGenerator ids) {
        this.access = access;
        this.members = members;
        this.posts = posts;
        this.requests = requests;
        this.requestQueries = requestQueries;
        this.changes = changes;
        this.groupPosts = groupPosts;
        this.policy = policy;
        this.people = people;
        this.notices = notices;
        this.ids = ids;
    }

    /** 보는 사람의 이 소모임 안 역할 — 모임장 · 맡은 매니저(처리자) · 맡지 않은 매니저 · 그 밖 */
    private record Actor(boolean leader, boolean manager, boolean handler) {

        String role() {
            return leader ? ROLE_LEADER : ROLE_MANAGER;
        }
    }

    private Actor actorOf(GroupEntity group, long userId) {
        if (group.getLeaderId().equals(userId)) {
            return new Actor(true, false, true);
        }
        GroupMemberEntity row = access.membership(group.getId(), userId).orElse(null);
        boolean manager = row != null && row.getRole() == GroupRole.MANAGER;
        return new Actor(false, manager, manager && (row.getManagerPerms() & GroupManagerPermission.UNPUBLISH.bit()) != 0);
    }

    // ═══ 글 단위: PUT …/posts/{postId}/visibility (11-6) ═══

    /**
     * 한 경로의 네 가지 일. 판정 차례: 401 → 소모임(404 · 403 {@code GROUP_INACTIVE}) → 글(이 소모임의 지우지 않고 가리지 않은 글 — 아니면 404 {@code POST_NOT_FOUND})
     * → {@code visibility}(400) → 역할 → 쓰기 자격 → {@code acknowledged}(공개로 바꿀 때, 400) → 바꾸기(이미 그 값이면 그대로 200).
     * <ul>
     *   <li>비공개 소모임 PUBLIC — 기능 켜짐: 모임장·맡은 매니저의 <b>직접 공개</b>(대기 요청이 있으면 승인으로 닫고 {@code GROUP_PUBLISH_APPROVED}, 없으면 {@code GROUP_POST_PUBLISHED}),
     *       작성자(일반 멤버) 409 {@code GROUP_PUBLISH_REQUEST_REQUIRED}, 맡지 않은 매니저 403 {@code GROUP_MANAGER_PERMISSION_DENIED}, 그 밖 403 {@code FORBIDDEN}.
     *       기능 꺼짐: 2단계 그대로(작성자만 · 지금 멤버 · 쓰기 자격).</li>
     *   <li>비공개 소모임 PRIVATE — <b>되돌리기</b>: 작성자(강퇴됐어도 자기 글, 자격 검사 없음 — 2단계) · 모임장 · 맡은 매니저(쓰기 자격). 남이 하면 {@code GROUP_POST_UNPUBLISHED},
     *       승인된 요청 행은 {@code CANCELED(UNPUBLISHED)}.</li>
     *   <li>공개 소모임 PRIVATE — 모임장·맡은 매니저의 <b>멤버 전용으로 돌리기</b>({@code acknowledged} 불필요, 남의 글이면 {@code GROUP_POST_MEMBERS_ONLY}). 작성자 409 {@code GROUP_POST_PUBLIC_BY_GROUP}.</li>
     *   <li>공개 소모임 PUBLIC — 모임장·맡은 매니저의 <b>다시 공개</b>(남의 글이면 {@code GROUP_POST_RELEASED}). 작성자 403 {@code FORBIDDEN}.</li>
     * </ul>
     */
    @Transactional
    public GroupPostVisibilityView setPostVisibility(String rawId, String rawPostId, Long userId, GroupPostVisibilityRequest request) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        Instant now = Instant.now();
        PostEntity post = lockGroupPost(group, rawPostId, now);
        String raw = request == null || request.visibility() == null ? "" : request.visibility().strip();
        boolean makePublic;
        if (GroupVisibility.PUBLIC.name().equals(raw)) {
            makePublic = true;
        } else if (GroupVisibility.PRIVATE.name().equals(raw)) {
            makePublic = false;
        } else {
            throw ValidationException.of("visibility", new MessageRef("group.post.visibility.invalid"));
        }
        boolean acknowledged = request != null && Boolean.TRUE.equals(request.acknowledged());
        boolean author = post.getAuthorId() != null && post.getAuthorId() == me;
        Actor actor = actorOf(group, me);

        if (group.isPublicGroup()) {
            return publicGroupPost(group, post, me, author, actor, makePublic, acknowledged, now);
        }
        if (makePublic) {
            if (!policy.enabled()) {
                return legacyPublish(group, post, me, author, acknowledged, now);
            }
            if (!actor.handler()) {
                if (actor.manager()) {
                    throw new GroupManagerPermissionDeniedException();
                }
                if (author) {
                    throw new GroupPublishRequestRequiredException();
                }
                throw new GroupForbiddenException();
            }
            access.requireWriter(me);
            if (post.isPublicPost()) {
                return result(group, post);
            }
            requireAcknowledged(acknowledged, "group.post.acknowledged.required");
            GroupPublishRequestEntity pending = requests.findById(post.getId()).filter(GroupPublishRequestEntity::pending).orElse(null);
            post.grantPublic(now, me);
            if (pending != null) {
                pending.approve(me, now);
            }
            log.info("소모임 글 직접 공개 groupId={} postId={} by={} role={} approvedRequest={}", group.getId(), post.getId(), me, actor.role(), pending != null);
            if (!author) {
                notices.postDecision(pending != null ? MemberNotificationType.GROUP_PUBLISH_APPROVED : MemberNotificationType.GROUP_POST_PUBLISHED, group,
                        post.getAuthorId(), post.getId(), post.getTitle(), me, actor.role(), null);
            }
            return result(group, post);
        }

        // 비공개 소모임의 개별 공개 철회 — 작성자 · 모임장 · 맡은 매니저(맡지 않은 매니저 403 PERMISSION_DENIED, 그 밖 403 FORBIDDEN — 2단계와 같다)
        if (!author) {
            if (!actor.handler()) {
                if (actor.manager()) {
                    throw new GroupManagerPermissionDeniedException();
                }
                throw new NotAuthorException();
            }
            access.requireWriter(me);
        }
        if (!post.isPublicPost()) {
            return result(group, post);
        }
        post.revokePublic(now);
        requests.findById(post.getId()).filter(r -> r.getStatus() == GroupPublishRequestStatus.APPROVED)
                .ifPresent(r -> r.cancel(GroupPublishRequestEntity.CANCEL_UNPUBLISHED));
        log.info("소모임 글 비공개로 되돌림 groupId={} postId={} by={}({})", group.getId(), post.getId(), me, author ? "AUTHOR" : actor.role());
        if (!author && post.getAuthorId() != null) {
            notices.postDecision(MemberNotificationType.GROUP_POST_UNPUBLISHED, group, post.getAuthorId(), post.getId(), post.getTitle(), me, actor.role(), null);
        }
        return result(group, post);
    }

    /** 기능이 꺼져 있을 때의 공개 전환(2단계) — 작성자만, 지금 멤버, 쓰기 자격, {@code acknowledged}. 개별 공개 시각·처리자를 함께 남긴다(켜졌을 때 댓글 가림의 기준) */
    private GroupPostVisibilityView legacyPublish(GroupEntity group, PostEntity post, long me, boolean author, boolean acknowledged, Instant now) {
        if (!author) {
            throw new NotAuthorException();
        }
        if (!isMember(group, me)) {
            throw new GroupNotMemberException();
        }
        access.requireWriter(me);
        if (post.isPublicPost()) {
            return result(group, post);
        }
        requireAcknowledged(acknowledged, "group.post.acknowledged.required");
        post.grantPublic(now, me);
        log.info("소모임 글 공개 전환 groupId={} postId={} userId={}", group.getId(), post.getId(), me);
        return result(group, post);
    }

    /** 공개 소모임의 글 — 모임장·맡은 매니저의 멤버 전용으로 돌리기(PRIVATE)·다시 공개(PUBLIC). 작성자(일반 멤버)는 둘 다 못 한다(R-21) */
    private GroupPostVisibilityView publicGroupPost(GroupEntity group, PostEntity post, long me, boolean author, Actor actor, boolean makePublic,
                                                    boolean acknowledged, Instant now) {
        if (!actor.handler()) {
            if (actor.manager()) {
                throw new GroupManagerPermissionDeniedException();
            }
            if (author && !makePublic) {
                throw new GroupPostPublicByGroupException();
            }
            throw new GroupForbiddenException();
        }
        access.requireWriter(me);
        if (makePublic) {
            if (post.isPublicPost()) {
                return result(group, post);
            }
            requireAcknowledged(acknowledged, "group.post.acknowledged.required");
            post.releaseMembersOnly(now);
            log.info("소모임 글 다시 공개 groupId={} postId={} by={} role={}", group.getId(), post.getId(), me, actor.role());
            if (!author && post.getAuthorId() != null) {
                notices.postDecision(MemberNotificationType.GROUP_POST_RELEASED, group, post.getAuthorId(), post.getId(), post.getTitle(), me, actor.role(), null);
            }
            return result(group, post);
        }
        if (!post.isPublicPost()) {
            return result(group, post);
        }
        post.markMembersOnly(now, me);
        log.info("소모임 글 멤버 전용으로 돌림 groupId={} postId={} by={} role={}", group.getId(), post.getId(), me, actor.role());
        if (!author && post.getAuthorId() != null) {
            notices.postDecision(MemberNotificationType.GROUP_POST_MEMBERS_ONLY, group, post.getAuthorId(), post.getId(), post.getTitle(), me, actor.role(), null);
        }
        return result(group, post);
    }

    private GroupPostVisibilityView result(GroupEntity group, PostEntity post) {
        boolean visible = post.isPublicPost();
        String publicBy = null;
        if (visible) {
            publicBy = group.isPublicGroup() ? GroupPostGate.BY_GROUP
                    : requests.findById(post.getId()).filter(r -> r.getStatus() == GroupPublishRequestStatus.APPROVED).isPresent() ? GroupPostGate.BY_APPROVED
                    : GroupPostGate.BY_DIRECT;
        }
        return new GroupPostVisibilityView(visible ? GroupVisibility.PUBLIC.name() : GroupVisibility.PRIVATE.name(), publicBy,
                group.isPublicGroup() && post.getMembersOnlyAt() != null);
    }

    // ═══ 공개 요청 (11-5) ═══

    /**
     * 작성자의 공개 요청(201). 판정 차례: 401 → 쓰기 자격(403) → 기능(403 {@code GROUP_VISIBILITY_DISABLED}) → 소모임(404 · 403) → 글(404) → 작성자(403 {@code FORBIDDEN})
     * · 지금 멤버(403 {@code GROUP_NOT_MEMBER}) → 처리자면 409 {@code GROUP_PUBLISH_REQUEST_NOT_NEEDED} → 공개 소모임이면 409 {@code GROUP_POST_ALREADY_PUBLIC}·{@code GROUP_POST_MEMBERS_ONLY}
     * → 이미 공개 409 {@code GROUP_POST_ALREADY_PUBLIC} → 대기 중 409 {@code GROUP_PUBLISH_REQUEST_EXISTS} → 거절 한도 409 {@code GROUP_PUBLISH_REQUEST_LIMIT} → 재요청 대기 429
     * {@code GROUP_PUBLISH_REQUEST_RETRY_LATER} → 하루 한도 429 {@code RATE_LIMITED} → {@code note} 길이 400 → {@code acknowledged} 400. 모임장·맡은 매니저에게 알림.
     */
    @Transactional
    public GroupVisibilityViews.PublishResult requestPublish(String rawId, String rawPostId, Long userId, GroupVisibilityRequests.Publish body) {
        long me = GroupAccess.requireUser(userId);
        access.requireWriter(me);
        requireEnabled();
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        Instant now = Instant.now();
        PostEntity post = lockGroupPost(group, rawPostId, now);
        if (post.getAuthorId() == null || post.getAuthorId() != me) {
            throw new GroupForbiddenException();
        }
        if (!isMember(group, me)) {
            throw new GroupNotMemberException();
        }
        if (actorOf(group, me).handler()) {
            throw new GroupPublishRequestNotNeededException();
        }
        if (group.isPublicGroup()) {
            if (post.isPublicPost()) {
                throw new GroupPostAlreadyPublicException();
            }
            throw new GroupPostMembersOnlyException();
        }
        if (post.isPublicPost()) {
            throw new GroupPostAlreadyPublicException();
        }
        GroupRuleValues.Visibility rules = policy.rules();
        GroupPublishRequestEntity existing = requests.findById(post.getId()).orElse(null);
        if (existing != null) {
            if (existing.pending()) {
                throw new GroupPublishRequestExistsException();
            }
            if (existing.getStatus() == GroupPublishRequestStatus.REJECTED) {
                if (existing.getRejectCount() >= rules.rejectLimit()) {
                    throw new GroupPublishRequestLimitException();
                }
                Instant retryAt = GroupPostGate.retryAt(existing, rules);
                if (retryAt != null && retryAt.isAfter(now)) {
                    throw new GroupPublishRequestRetryLaterException(Duration.between(now, retryAt).toSeconds());
                }
            }
        }
        Instant since = now.minus(24, ChronoUnit.HOURS);
        if (requestQueries.countSince(me, since) >= rules.dailyLimit()) {
            Instant oldest = requestQueries.oldestSince(me, since);
            throw new GroupPublishRateLimitedException(oldest == null ? 3600 : Duration.between(now, oldest.plus(24, ChronoUnit.HOURS)).toSeconds());
        }
        String note = cleanText(body == null ? null : body.note(), "note", "group.publish.note.length", rules.textMax());
        requireAcknowledged(body != null && Boolean.TRUE.equals(body.acknowledged()), "group.publish.acknowledged.required");
        if (existing == null) {
            requests.persist(GroupPublishRequestEntity.create(post.getId(), group.getId(), me, note, now));
        } else {
            existing.reopen(note, now);
        }
        log.info("소모임 글 공개 요청 groupId={} postId={} userId={}", group.getId(), post.getId(), me);
        for (long recipientId : handlers(group)) {
            notices.publishRequested(group, recipientId, post.getId(), post.getTitle(), me); // 요청한 사람 본인은 발행기가 거른다
        }
        return new GroupVisibilityViews.PublishResult(GroupPublishRequestStatus.PENDING.name(), now);
    }

    /** 작성자가 자기 요청을 거둔다(204) — 요청이 없거나 이미 처리됐어도 성공(멱등). 처리자 쪽 알림은 회수하지 않는다. 401 → 쓰기 자격(403) → 소모임(404 · 403) */
    @Transactional
    public void cancelRequest(String rawId, String rawPostId, Long userId) {
        long me = GroupAccess.requireUser(userId);
        access.requireWriter(me);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        Long postId = PostQueryService.parseId(rawPostId);
        if (postId == null) {
            return;
        }
        requests.findById(postId).filter(r -> r.getGroupId().equals(group.getId()) && r.getRequesterId() == me && r.pending())
                .ifPresent(r -> r.cancel(GroupPublishRequestEntity.CANCEL_AUTHOR));
    }

    /**
     * 처리 대기 목록 — 모임장·맡은 매니저(403 {@code GROUP_LEADER_ONLY}·{@code GROUP_MANAGER_PERMISSION_DENIED}). 열람이라 쓰기 자격(정지·미인증)은 묻지 않는다.
     * {@code status} 는 PENDING 만(400 {@code fields.status}), 커서 400 {@code fields.cursor}, 크기 1~50(400 {@code fields.size}). 기능이 꺼져 있으면 빈 목록.
     */
    @Transactional(readOnly = true)
    public GroupVisibilityViews.RequestList listRequests(String rawId, Long userId, String statusRaw, String cursorRaw, String sizeRaw) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.active(GroupAccess.parseId(rawId));
        access.requireCapability(group, me, GroupManagerPermission.UNPUBLISH);
        if (statusRaw != null && !statusRaw.isBlank() && !GroupPublishRequestStatus.PENDING.name().equals(statusRaw.strip())) {
            throw ValidationException.of("status", new MessageRef("group.publish.status.invalid"));
        }
        Instant afterAt = null;
        Long afterPostId = null;
        if (cursorRaw != null && !cursorRaw.isBlank()) {
            String[] parts = cursorRaw.strip().split("_");
            try {
                if (parts.length != 2) {
                    throw new IllegalArgumentException();
                }
                afterAt = Instant.EPOCH.plus(Long.parseLong(parts[0]), ChronoUnit.MICROS);
                afterPostId = Long.parseLong(parts[1]);
            } catch (RuntimeException e) {
                throw ValidationException.of("cursor", new MessageRef("group.post.cursor.invalid"));
            }
        }
        Integer size = GroupQueryService.intIn(sizeRaw, DEFAULT_SIZE, 1, MAX_SIZE);
        if (size == null) {
            throw ValidationException.of("size", new MessageRef("group.post.size.invalid", String.valueOf(MAX_SIZE)));
        }
        GroupRuleValues.Visibility rules = policy.rules();
        if (!rules.visibilityEnabled()) {
            return new GroupVisibilityViews.RequestList(List.of(), false, null, 0, rules.textMax());
        }
        Instant now = Instant.now();
        List<GroupPublishRequestQueries.PendingRow> rows = new ArrayList<>(requestQueries.pending(group.getId(), afterAt, afterPostId, size + 1, now));
        boolean hasNext = rows.size() > size;
        if (hasNext) {
            rows = rows.subList(0, size);
        }
        List<Long> authorIds = rows.stream().map(GroupPublishRequestQueries.PendingRow::authorId).filter(java.util.Objects::nonNull).distinct().toList();
        GroupPeople.Members persons = people.members(authorIds, true);
        Set<Long> active = new HashSet<>();
        if (!authorIds.isEmpty()) {
            members.findByGroupIdAndUserIdIn(group.getId(), authorIds).stream().filter(GroupMemberEntity::active).forEach(m -> active.add(m.getUserId()));
            if (authorIds.contains(group.getLeaderId())) {
                active.add(group.getLeaderId());
            }
        }
        List<GroupVisibilityViews.RequestRow> items = rows.stream().map(r -> {
            GroupPublishRequestEntity req = r.request();
            boolean edited = r.postEditedAt() != null && r.postEditedAt().isAfter(req.getRequestedAt());
            return new GroupVisibilityViews.RequestRow(new GroupVisibilityViews.RequestPost(Long.toString(req.getPostId()), r.title(), r.postCreatedAt(),
                    r.postEditedAt()), r.authorId() == null ? null : GroupUsers.of(persons.of(r.authorId())), req.getNote(), req.getRequestedAt(), edited,
                    r.authorId() != null && active.contains(r.authorId()), req.getRejectCount());
        }).toList();
        String nextCursor = null;
        if (hasNext && !rows.isEmpty()) {
            GroupPublishRequestEntity last = rows.getLast().request();
            nextCursor = ChronoUnit.MICROS.between(Instant.EPOCH, last.getRequestedAt()) + "_" + last.getPostId();
        }
        return new GroupVisibilityViews.RequestList(items, hasNext, nextCursor, requestQueries.pendingCount(group.getId(), now), rules.textMax());
    }

    /**
     * 승인(200) — 글이 모두에게 공개되고 작성자에게 {@code GROUP_PUBLISH_APPROVED}. 판정 차례: 401 → 쓰기 자격 → 기능 → 소모임 → 역할 → 요청(없음·취소 404
     * {@code GROUP_PUBLISH_REQUEST_NOT_FOUND}, 처리됨 409 {@code GROUP_PUBLISH_REQUEST_DECIDED}) → 글(지웠거나 가렸으면 404 {@code POST_NOT_FOUND} — 요청 행은 그대로) → {@code acknowledged} 400.
     */
    @Transactional
    public GroupPostVisibilityView approve(String rawId, String rawPostId, Long userId, GroupVisibilityRequests.Approve body) {
        long me = GroupAccess.requireUser(userId);
        access.requireWriter(me);
        requireEnabled();
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        GroupRole role = access.requireCapability(group, me, GroupManagerPermission.UNPUBLISH);
        GroupPublishRequestEntity request = pendingRequest(group, rawPostId);
        Instant now = Instant.now();
        PostEntity post = lockGroupPost(group, rawPostId, now);
        requireAcknowledged(body != null && Boolean.TRUE.equals(body.acknowledged()), "group.publish.approve.acknowledged.required");
        post.grantPublic(now, me);
        request.approve(me, now);
        log.info("소모임 글 공개 승인 groupId={} postId={} by={} role={}", group.getId(), post.getId(), me, role);
        if (post.getAuthorId() != null && post.getAuthorId() != me) {
            notices.postDecision(MemberNotificationType.GROUP_PUBLISH_APPROVED, group, post.getAuthorId(), post.getId(), post.getTitle(), me, role.name(), null);
        }
        return new GroupPostVisibilityView(GroupVisibility.PUBLIC.name(), GroupPostGate.BY_APPROVED, false);
    }

    /** 거절(200) — 거절 횟수가 늘고 작성자에게 {@code GROUP_PUBLISH_REJECTED}(사유가 있으면 함께). 글은 계속 멤버 전용. 판정 차례는 승인과 같고 마지막이 {@code reason} 길이(400) */
    @Transactional
    public GroupVisibilityViews.RejectResult reject(String rawId, String rawPostId, Long userId, GroupVisibilityRequests.Reject body) {
        long me = GroupAccess.requireUser(userId);
        access.requireWriter(me);
        requireEnabled();
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        GroupRole role = access.requireCapability(group, me, GroupManagerPermission.UNPUBLISH);
        GroupPublishRequestEntity request = pendingRequest(group, rawPostId);
        Instant now = Instant.now();
        PostEntity post = lockGroupPost(group, rawPostId, now);
        String reason = cleanText(body == null ? null : body.reason(), "reason", "group.publish.reason.length", policy.rules().textMax());
        request.reject(me, reason, now);
        log.info("소모임 글 공개 거절 groupId={} postId={} by={} role={}", group.getId(), post.getId(), me, role);
        if (post.getAuthorId() != null && post.getAuthorId() != me) {
            notices.postDecision(MemberNotificationType.GROUP_PUBLISH_REJECTED, group, post.getAuthorId(), post.getId(), post.getTitle(), me, role.name(), reason);
        }
        return new GroupVisibilityViews.RejectResult(GroupPublishRequestStatus.REJECTED.name(), request.getRejectCount());
    }

    /** 처리할 요청 — 없거나 작성자가 거뒀으면 404, 이미 승인·거절됐으면 409 */
    private GroupPublishRequestEntity pendingRequest(GroupEntity group, String rawPostId) {
        Long postId = PostQueryService.parseId(rawPostId);
        GroupPublishRequestEntity request = postId == null ? null : requests.findById(postId).filter(r -> r.getGroupId().equals(group.getId())).orElse(null);
        if (request == null || request.getStatus() == GroupPublishRequestStatus.CANCELED) {
            throw new GroupPublishRequestNotFoundException();
        }
        if (!request.pending()) {
            throw new GroupPublishRequestDecidedException();
        }
        return request;
    }

    // ═══ 소모임 공개 범위 (11-4) ═══

    /** 확인 시트의 숫자 — 모임장만(403 {@code GROUP_LEADER_ONLY}, 열람이라 쓰기 자격은 묻지 않는다). {@code to} 가 두 값이 아니면 400 {@code fields.to}, 지금 값과 같으면 모든 숫자가 0 */
    @Transactional(readOnly = true)
    public GroupVisibilityViews.Preview preview(String rawId, Long userId, String toRaw) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.active(GroupAccess.parseId(rawId));
        access.requireLeader(group, me);
        GroupVisibility to = GroupVisibility.parse(toRaw);
        if (to == null) {
            throw ValidationException.of("to", new MessageRef("group.visibility.invalid"));
        }
        if (to == group.getVisibility()) {
            return new GroupVisibilityViews.Preview(to.name(), 0, 0, 0, 0, 0, null);
        }
        long groupId = group.getId();
        Instant now = Instant.now();
        if (to == GroupVisibility.PUBLIC) {
            return new GroupVisibilityViews.Preview(to.name(), groupPosts.countOpenable(groupId, now), groupPosts.countCommentsOfOpenable(groupId, now),
                    groupPosts.countIndividuallyPublic(groupId, now), groupPosts.countMembersOnly(groupId, now), (int) requestQueries.pendingCount(groupId, now),
                    GroupVisibilityPolicy.canChangeToPublicAt(group, policy.rules().cooldownDays(), now));
        }
        return new GroupVisibilityViews.Preview(to.name(), groupPosts.countVisibleToAll(groupId, now), groupPosts.countCommentsOfVisibleToAll(groupId, now),
                groupPosts.countIndividuallyPublic(groupId, now), groupPosts.countMembersOnly(groupId, now), 0, null);
    }

    /**
     * 공개 범위 변경(200). 판정 차례: 401 → 소모임(404 · 403) → 모임장(403 {@code GROUP_LEADER_ONLY} — 매니저 포함) → 쓰기 자격(403) → {@code visibility}(400) → 같은 값이면 그대로 200
     * (이력·알림 없음) → 비공개→공개: 기능(403 {@code GROUP_VISIBILITY_DISABLED}) → {@code acknowledged}(400) → 쿨다운(409 {@code GROUP_VISIBILITY_COOLDOWN}, {@code retryAfterSeconds}).
     * 같은 트랜잭션: 소모임 값·변경 시각 → 글 일괄 갱신 → 이력 → (PUBLIC 이면) 대기 요청 {@code CANCELED(GROUP_PUBLIC)} → 멤버 전원에게 {@code GROUP_VISIBILITY_CHANGED}(바꾼 모임장 제외).
     */
    @Transactional
    public GroupVisibilityViews.ChangeResult changeVisibility(String rawId, Long userId, GroupVisibilityRequests.Change request) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        access.requireLeader(group, me);
        access.requireWriter(me);
        GroupVisibility to = request == null ? null : GroupVisibility.parse(request.visibility());
        if (to == null) {
            throw ValidationException.of("visibility", new MessageRef("group.visibility.invalid"));
        }
        if (to == group.getVisibility()) {
            return new GroupVisibilityViews.ChangeResult(to.name(), group.getVisibilityChangedAt(), new GroupVisibilityViews.Affected(0, 0, 0));
        }
        Instant now = Instant.now();
        long groupId = group.getId();
        GroupVisibility from = group.getVisibility();
        int membersOnly = groupPosts.countMembersOnly(groupId, now);
        int postCount;
        int commentCount;
        if (to == GroupVisibility.PUBLIC) {
            GroupRuleValues.Visibility rules = policy.rules();
            if (!rules.visibilityEnabled()) {
                throw new GroupVisibilityDisabledException();
            }
            requireAcknowledged(Boolean.TRUE.equals(request.acknowledged()), "group.visibility.acknowledged.required");
            Instant until = GroupVisibilityPolicy.canChangeToPublicAt(group, rules.cooldownDays(), now);
            if (until != null) {
                throw new GroupVisibilityCooldownException(Duration.between(now, until).toSeconds());
            }
            postCount = groupPosts.countOpenable(groupId, now);
            commentCount = groupPosts.countCommentsOfOpenable(groupId, now);
            group.changeVisibility(to, me, now);
            groupPosts.openAllExceptMembersOnly(groupId, now);
            requestQueries.cancelPendingOfGroup(groupId, GroupPublishRequestEntity.CANCEL_GROUP_PUBLIC);
        } else {
            postCount = groupPosts.countVisibleToAll(groupId, now);
            commentCount = groupPosts.countCommentsOfVisibleToAll(groupId, now);
            group.changeVisibility(to, me, now);
            groupPosts.closeAll(groupId, now);
        }
        changes.persist(GroupVisibilityChangeEntity.of(ids.nextId(), groupId, from, to, me, now, postCount, commentCount, membersOnly));
        log.info("소모임 공개 범위 변경 groupId={} {}→{} by={} posts={} comments={} membersOnly={}", groupId, from, to, me, postCount, commentCount, membersOnly);
        for (GroupMemberEntity m : members.findByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE)) {
            if (m.getUserId() != me) {
                notices.visibilityChanged(group, m.getUserId(), me);
            }
        }
        return new GroupVisibilityViews.ChangeResult(to.name(), now, new GroupVisibilityViews.Affected(postCount, commentCount, membersOnly));
    }

    // ═══ 안쪽 ═══

    private void requireEnabled() {
        if (!policy.enabled()) {
            throw new GroupVisibilityDisabledException();
        }
    }

    private static void requireAcknowledged(boolean acknowledged, String key) {
        if (!acknowledged) {
            throw ValidationException.of("acknowledged", new MessageRef(key));
        }
    }

    /** 공개 요청을 받는 사람 — 모임장 + 「글 공개 처리·되돌리기」를 맡은 지금 매니저 */
    private Set<Long> handlers(GroupEntity group) {
        Set<Long> out = new LinkedHashSet<>();
        out.add(group.getLeaderId());
        out.addAll(requestQueries.managerIdsWith(group.getId(), GroupManagerPermission.UNPUBLISH));
        return out;
    }

    /** 한마디·사유 — 공백을 다듬고 비어 있으면 null, 지금 한도(textMax)를 넘으면 칸 오류 */
    private static String cleanText(String raw, String field, String key, int max) {
        String text = raw == null ? "" : raw.replace("\u0000", "").strip();
        if (text.isEmpty()) {
            return null;
        }
        if (text.codePointCount(0, text.length()) > max) {
            throw ValidationException.of(field, new MessageRef(key, String.valueOf(max)));
        }
        return text;
    }

    /** 이 소모임의 지우지 않고 가리지 않은(숨김 · 나가서 가림 아님) 글을 잠그고 읽는다 — 아니면 404 {@code POST_NOT_FOUND} */
    private PostEntity lockGroupPost(GroupEntity group, String rawPostId, Instant now) {
        Long postId = PostQueryService.parseId(rawPostId);
        PostEntity post = postId == null ? null : posts.findWithLockById(postId).orElse(null);
        if (post == null || !group.getId().equals(post.getGroupId()) || post.isGoneAt(now) || post.isHiddenAt(now) || post.isGroupLeft()) {
            throw new PostNotFoundException();
        }
        return post;
    }

    /** 지금 멤버(모임장 포함)인가 */
    private boolean isMember(GroupEntity group, long userId) {
        return group.getLeaderId().equals(userId) || access.membership(group.getId(), userId).isPresent();
    }
}
