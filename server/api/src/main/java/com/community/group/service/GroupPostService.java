package com.community.group.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.dto.response.BoardDetailView;
import com.community.board.exception.NotAuthorException;
import com.community.board.service.BoardAccessPolicy;
import com.community.board.service.BoardCatalog;
import com.community.board.service.PublicBoardService;
import com.community.group.dto.request.GroupPostVisibilityRequest;
import com.community.group.dto.response.GroupPostPageView;
import com.community.group.dto.response.GroupPostRowView;
import com.community.group.dto.response.GroupPostVisibilityView;
import com.community.group.dto.response.GroupPostWriteResultView;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupManagerPermission;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupRole;
import com.community.group.entity.GroupStatus;
import com.community.group.exception.GroupManagerPermissionDeniedException;
import com.community.group.exception.GroupNotFoundException;
import com.community.group.exception.GroupNotMemberException;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.group.repository.master.GroupPostQueries;
import com.community.group.repository.master.GroupRepository;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.post.dto.request.PostWriteRequest;
import com.community.post.dto.response.PostGroupView;
import com.community.post.dto.response.PostRowView;
import com.community.post.entity.PostEntity;
import com.community.post.exception.PostNotFoundException;
import com.community.post.repository.master.PostRepository;
import com.community.post.service.PostFeedRows;
import com.community.post.service.PostQueryService;
import com.community.post.service.PostWriteService;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 소모임 글(community api-groups.md 8절 — 11절 공개 범위·공개 요청은 5단계) — 글 탭 · 글쓰기 · 공개 전환/되돌리기 · 「봤어요」 · 소모임 게시판 정보(로보얌 추가).
 *
 * <p>소모임 글은 {@code posts} 표의 글이다({@code group_id}·{@code is_public} — V91). <b>비공개로 시작해 그 소모임의 멤버에게만 보이고</b>, 작성자가 공개로 전환하면 누구에게나 보인다.
 * 글 상세·고치기·지우기·추천·댓글·투표는 기존 글 API({@code /api/posts/{id}…})를 그대로 쓰고 판정은 {@link GroupPostGate} 한 곳이다.
 * 쓰기·전환은 소모임 행을 잠그고 한다(탈퇴·강퇴·해산과 한 줄로 서도록).
 */
@Service
public class GroupPostService {

    private static final Logger log = LoggerFactory.getLogger(GroupPostService.class);

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;
    static final String ROLE_LEADER = "LEADER";
    static final String ROLE_MANAGER = "MANAGER";

    private final GroupAccess access;
    private final GroupPostQueries postQueries;
    private final GroupMemberRepository members;
    private final GroupRepository groups;
    private final PostRepository posts;
    private final PostFeedRows rows;
    private final PostWriteService writes;
    private final BoardCatalog catalog;
    private final MemberNotificationPublisher notices;
    private final TransactionTemplate tx;

    public GroupPostService(GroupAccess access, GroupPostQueries postQueries, GroupMemberRepository members, GroupRepository groups, PostRepository posts,
                            PostFeedRows rows,
                            PostWriteService writes, BoardCatalog catalog, MemberNotificationPublisher notices, PlatformTransactionManager txManager) {
        this.access = access;
        this.postQueries = postQueries;
        this.members = members;
        this.groups = groups;
        this.posts = posts;
        this.rows = rows;
        this.writes = writes;
        this.catalog = catalog;
        this.notices = notices;
        this.tx = new TransactionTemplate(txManager);
    }

    // ── 글 탭 ────────────────────────────────────────────────────────────────

    /**
     * 글 탭 — 멤버(모임장 포함)는 비공개 글까지, 비멤버·비회원은 공개로 전환된 글만. 지운 글·숨긴 글·나간 멤버의 글은 없다. 최신이 위, 커서({@code cursor} = 앞 쪽 마지막 글 ID).
     * 열려 있지 않은 소모임 404 {@code GROUP_NOT_FOUND}·403 {@code GROUP_INACTIVE}. 잘못된 커서 {@code 400 fields.cursor}, 크기 1~50 밖 {@code 400 fields.size}.
     */
    @Transactional(readOnly = true)
    public GroupPostPageView list(String rawId, Long userId, String cursorRaw, String sizeRaw) {
        GroupEntity group = access.active(GroupAccess.parseId(rawId));
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        Long cursor = null;
        if (cursorRaw != null && !cursorRaw.isBlank()) {
            cursor = PostQueryService.parseId(cursorRaw);
            if (cursor == null) {
                invalid.put("cursor", new MessageRef("group.post.cursor.invalid"));
            }
        }
        Integer size = GroupQueryService.intIn(sizeRaw, DEFAULT_SIZE, 1, MAX_SIZE);
        if (size == null) {
            invalid.put("size", new MessageRef("group.post.size.invalid", String.valueOf(MAX_SIZE)));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        boolean member = isMember(group, userId);
        Instant now = Instant.now();
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = s.board(group.getBoardId()).orElseThrow(GroupNotFoundException::new);
        List<GroupPostQueries.Row> found = new ArrayList<>(postQueries.rows(group.getId(), member, cursor, size + 1, now));
        boolean hasNext = found.size() > size;
        List<GroupPostQueries.Row> page = hasNext ? found.subList(0, size) : found;
        List<PostRowView> views = rows.boardRows(page.stream().map(GroupPostQueries.Row::data).toList(), b, s, now, userId);
        List<GroupPostRowView> items = new ArrayList<>(views.size());
        String groupId = Long.toString(group.getId());
        for (int i = 0; i < views.size(); i++) {
            items.add(new GroupPostRowView(views.get(i), PostGroupView.ref(groupId, group.getName(), page.get(i).publicPost(), false)));
        }
        String next = hasNext && !page.isEmpty() ? Long.toString(page.getLast().data().id()) : null;
        boolean locked = !member;
        return new GroupPostPageView(items, hasNext, next, locked, locked ? postQueries.privateCount(group.getId(), now) : 0);
    }

    /** 글 탭을 봤다(204) — 내 마지막으로 본 시각을 지금으로. 멤버가 아니거나 없는 소모임이면 아무 일도 없다(비회원 401) */
    @Transactional
    public void seen(String rawId, Long userId) {
        long me = GroupAccess.requireUser(userId);
        Long id = GroupQueryService.parseLong(rawId);
        if (id == null) {
            return;
        }
        members.findByGroupIdAndUserId(id, me).filter(GroupMemberEntity::active).ifPresent(m -> m.seen(Instant.now()));
    }

    // ── 글쓰기 ───────────────────────────────────────────────────────────────

    /**
     * 글쓰기 — 비공개 글로 만든다. 판정 차례: 401 → 소모임 404 · 403 {@code GROUP_INACTIVE} → 멤버 403 {@code GROUP_NOT_MEMBER}(신청 대기·강퇴·탈퇴 포함) → 쓰기 자격
     * (403 {@code EMAIL_NOT_VERIFIED} → {@code ACCOUNT_RESTRICTED} → {@code BUSINESS_ACCOUNT_RESTRICTED} → 회원 역할 {@code post.write} 없음 {@code BOARD_RESTRICTED}) → 글 칸 400 →
     * 이미지 422 → 금지어 400 → 작성 간격·하루 한도 429 → 같은 글 409(일반 글과 같은 한도). 본문은 일반 글쓰기와 같고 {@code boardSlug}·{@code guest} 는 무시한다.
     * 소모임의 최근 활동 시각이 갱신된다.
     */
    public GroupPostWriteResultView create(String rawId, Long userId, PostWriteRequest body, String ip) {
        long me = GroupAccess.requireUser(userId);
        long groupId = GroupAccess.parseId(rawId);
        PostWriteRequest request = body == null ? new PostWriteRequest(null, null, null, null, null, null, null, null, null) : body;
        PostWriteService.GroupWrite written = tx.execute(status -> {
            GroupEntity group = access.lockActive(groupId);
            if (!isMember(group, me)) {
                throw new GroupNotMemberException();
            }
            BoardActor actor = access.actor(me);
            PostWriteService.requireWritable(GroupPostGate.writeBlocked(actor));
            BoardCatalogSnapshot s = catalog.snapshot();
            // 꺼진 게시판도 쓴다 — 관리자가 소모임 게시판을 꺼도 기존 소모임의 글쓰기는 막히지 않는다(소모임은 멈추지 않는다)
            EffectiveBoard b = s.board(group.getBoardId()).orElseThrow(GroupNotFoundException::new);
            PostWriteService.GroupWrite result = writes.createInGroup(group.getId(), b, actor, request, ip, s);
            group.touchActivity(Instant.now());
            return result;
        });
        writes.afterGroupWrite(me); // 커밋 뒤 — 활동 수 조건 즉시 승급(실패해도 글쓰기는 성공)
        log.info("소모임 글을 썼다 groupId={} postId={} userId={}", groupId, written.id(), me);
        return new GroupPostWriteResultView(Long.toString(written.id()), written.pointsEarned());
    }

    // ── 공개 전환 · 되돌리기 ───────────────────────────────────────────────────

    /**
     * 공개 전환·되돌리기(8절). 판정 차례: 401 → 소모임 404 · 403 {@code GROUP_INACTIVE} → 글(이 소모임의 글이 아니거나 없거나 지웠거나 숨겼거나 나가서 가렸으면 404
     * {@code POST_NOT_FOUND}) → {@code visibility}(400 {@code fields.visibility}) → 역할 → 쓰기 자격 → {@code acknowledged}(공개 전환만, 400) → 바꾸기(이미 그 값이면 그대로 200).
     * <ul>
     *   <li>PUBLIC — 작성자만(아니면 403 {@code FORBIDDEN}), 지금도 멤버(403 {@code GROUP_NOT_MEMBER} — 강퇴된 작성자 포함), 쓰기 자격.</li>
     *   <li>PRIVATE — 작성자(강퇴됐어도 자기 글, 자격 검사 없음) · 모임장 · 되돌리기({@code UNPUBLISH})를 맡은 매니저(그 일이 없는 매니저 403 {@code GROUP_MANAGER_PERMISSION_DENIED},
     *       그 밖 403 {@code FORBIDDEN}). 모임장·매니저는 쓰기 자격을 본다(정지 403 {@code ACCOUNT_RESTRICTED} 등). 모임장·매니저가 되돌리면 작성자에게 알림 {@code GROUP_POST_UNPUBLISHED}.</li>
     * </ul>
     */
    @Transactional
    public GroupPostVisibilityView visibility(String rawId, String rawPostId, Long userId, GroupPostVisibilityRequest request) {
        long me = GroupAccess.requireUser(userId);
        GroupEntity group = access.lockActive(GroupAccess.parseId(rawId));
        Instant now = Instant.now();
        Long postId = PostQueryService.parseId(rawPostId);
        PostEntity post = postId == null ? null : posts.findWithLockById(postId).orElse(null);
        if (post == null || !group.getId().equals(post.getGroupId()) || post.isGoneAt(now) || post.isHiddenAt(now) || post.isGroupLeft()) {
            throw new PostNotFoundException();
        }
        String raw = request == null || request.visibility() == null ? "" : request.visibility().strip();
        boolean toPublic;
        if (PostGroupView.PUBLIC.equals(raw)) {
            toPublic = true;
        } else if (PostGroupView.PRIVATE.equals(raw)) {
            toPublic = false;
        } else {
            throw ValidationException.of("visibility", new MessageRef("group.post.visibility.invalid"));
        }
        boolean author = post.getAuthorId() != null && post.getAuthorId() == me;
        String actorRole = null;
        if (toPublic) {
            if (!author) {
                throw new NotAuthorException();
            }
            if (!isMember(group, me)) {
                throw new GroupNotMemberException();
            }
            access.requireWriter(me);
            if (!Boolean.TRUE.equals(request.acknowledged())) {
                throw ValidationException.of("acknowledged", new MessageRef("group.post.acknowledged.required"));
            }
        } else if (!author) {
            actorRole = unpublisherRole(group, me);
            access.requireWriter(me);
        }
        boolean changed = post.changePublic(toPublic, now);
        if (changed && actorRole != null) {
            notices.groupPostUnpublished(post.getAuthorId(), group.getId(), group.getName(), post.getId(), post.getTitle(), me, actorRole);
        }
        if (changed) {
            log.info("소모임 글 공개 상태를 바꿨다 groupId={} postId={} visibility={} by userId={}({})", group.getId(), post.getId(), raw, me,
                    actorRole == null ? "AUTHOR" : actorRole);
        }
        return new GroupPostVisibilityView(post.isPublicPost() ? PostGroupView.PUBLIC : PostGroupView.PRIVATE);
    }

    /** 남의 글을 되돌릴 수 있는 역할 — 모임장 LEADER, 되돌리기를 맡은 매니저 MANAGER. 그 일이 없는 매니저 403 GROUP_MANAGER_PERMISSION_DENIED, 그 밖 403 FORBIDDEN */
    private String unpublisherRole(GroupEntity group, long me) {
        if (group.getLeaderId().equals(me)) {
            return ROLE_LEADER;
        }
        GroupMemberEntity row = access.membership(group.getId(), me).orElse(null);
        if (row == null || row.getRole() != GroupRole.MANAGER) {
            throw new NotAuthorException();
        }
        if ((row.getManagerPerms() & GroupManagerPermission.UNPUBLISH.bit()) == 0) {
            throw new GroupManagerPermissionDeniedException();
        }
        return ROLE_MANAGER;
    }

    // ── 소모임 게시판(로보얌 추가) ─────────────────────────────────────────────

    /**
     * {@code GET /api/groups/{id}/board} — 소모임 게시판 정보({@code GET /api/boards/{slug}} 와 같은 모양). 소모임 게시판은 공개 게시판 API 에서 없는 게시판이라
     * 소모임 글쓰기·글 상세(댓글)·글 고치기의 에디터·말머리·이미지·투표·댓글 설정을 여기서 준다. 열린 소모임과 해산한 소모임(공개로 남은 글의 상세·댓글)은 누구나 읽는다
     * (게시판이 꺼졌어도). 비활성 403 {@code GROUP_INACTIVE}(그 소모임 글 상세도 403 이라 부를 일이 없다), 신청 중·반려·없음 404.
     * {@code viewer.writeBlockedReason}: 비회원 {@code LOGIN_REQUIRED} · 해산 {@code GROUP_DISSOLVED} · 비멤버 {@code GROUP_NOT_MEMBER} · 쓰기 자격(소모임 글쓰기와 같은 차례).
     * {@code viewer.commentBlockedReason}: 계정 자격만(비공개 글은 글을 열 때 멤버만 — 글 상세가 거른다). 게시판 쓰기·댓글 정책(등급·잠금)은 받지 않는다.
     */
    @Transactional(readOnly = true)
    public BoardDetailView board(String rawId, Long userId) {
        long id = GroupAccess.parseId(rawId);
        GroupEntity group = groups.findById(id).orElseThrow(GroupNotFoundException::new);
        // 해산한 소모임은 공개로 남은 글(dissolved)의 상세·댓글 칸을 그리도록 읽기는 열고 새 글만 막는다(GROUP_DISSOLVED). 그 밖(비활성 403·신청 중·반려 404)은 열림만
        boolean dissolved = group.getStatus() == GroupStatus.DELETED && group.getOpenedAt() != null;
        if (!dissolved) {
            access.active(id);
        }
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = s.board(group.getBoardId()).orElseThrow(GroupNotFoundException::new);
        BoardActor actor = userId == null ? BoardActor.anonymous() : accessActor(userId);
        String writeBlocked = actor.guest() ? BoardAccessPolicy.LOGIN_REQUIRED
                : dissolved ? "GROUP_DISSOLVED"
                : !isMember(group, actor.userId()) ? "GROUP_NOT_MEMBER" : GroupPostGate.writeBlocked(actor);
        return PublicBoardService.detail(s, b, actor, writeBlocked, GroupPostGate.commentBlocked(actor));
    }

    private BoardActor accessActor(long userId) {
        try {
            return access.actor(userId);
        } catch (RuntimeException e) {
            return BoardActor.anonymous(); // 탈퇴·없는 회원 — 비회원으로 본다
        }
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    /** 지금 멤버(모임장 포함)인가 */
    private boolean isMember(GroupEntity group, Long userId) {
        return userId != null && (group.getLeaderId().equals(userId) || access.membership(group.getId(), userId).isPresent());
    }
}
