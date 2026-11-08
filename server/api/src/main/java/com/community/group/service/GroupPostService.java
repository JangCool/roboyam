package com.community.group.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.dto.response.BoardDetailView;
import com.community.board.service.BoardAccessPolicy;
import com.community.board.service.BoardCatalog;
import com.community.board.service.ContentRules;
import com.community.board.service.PublicBoardService;
import com.community.group.dto.response.GroupPostPageView;
import com.community.group.dto.response.GroupPostRowView;
import com.community.group.dto.response.GroupPostWriteResultView;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupStatus;
import com.community.group.exception.GroupNotFoundException;
import com.community.group.exception.GroupNotMemberException;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.group.repository.master.GroupPostQueries;
import com.community.group.repository.master.GroupRepository;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.notice.service.GlobalNoticeReader;
import com.community.post.dto.request.PostWriteRequest;
import com.community.post.dto.response.PostGroupView;
import com.community.post.dto.response.PostRowView;
import com.community.post.repository.master.PostRepository;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.post.service.PostFeedRows;
import com.community.post.service.PostQueryService;
import com.community.post.service.PostWriteService;
import com.community.post.service.SecretPostGate;
import com.community.post.entity.PostProcessStatus;
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
 * 소모임 글(community api-groups.md 8절) — 글 탭 · 글쓰기 · 「봤어요」 · 소모임 게시판 정보(로보얌 추가). 글 공개 전환·공개 요청·공개 범위(11절)는 {@link GroupPostVisibilityService}.
 *
 * <p>소모임 글은 {@code posts} 표의 글이다({@code group_id}·{@code is_public} — V91·V94). 비공개 소모임의 글은 <b>비공개로 시작해 멤버에게만 보이고</b> 개별 공개되면 누구에게나 보인다.
 * 공개 소모임의 글은 쓰는 순간 비멤버에게도 보인다(모임장·매니저가 멤버 전용으로 돌릴 수 있다).
 * 글 상세·고치기·지우기·추천·댓글·투표는 기존 글 API({@code /api/posts/{id}…})를 그대로 쓰고 판정은 {@link GroupPostGate} 한 곳이다.
 * 쓰기·전환은 소모임 행을 잠그고 한다(탈퇴·강퇴·해산과 한 줄로 서도록).
 */
@Service
public class GroupPostService {

    private static final Logger log = LoggerFactory.getLogger(GroupPostService.class);

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;

    private final GroupAccess access;
    private final GroupPostQueries postQueries;
    private final GroupMemberRepository members;
    private final GroupRepository groups;
    private final PostRepository posts;
    private final PostFeedRows rows;
    private final PostWriteService writes;
    private final BoardCatalog catalog;
    private final MemberNotificationPublisher notices;
    private final SecretPostGate secretGate;
    private final TransactionTemplate tx;
    private final GlobalNoticeReader globalNotices;

    public GroupPostService(GroupAccess access, GroupPostQueries postQueries, GroupMemberRepository members, GroupRepository groups, PostRepository posts,
                            PostFeedRows rows,
                            PostWriteService writes, BoardCatalog catalog, MemberNotificationPublisher notices, SecretPostGate secretGate,
                            PlatformTransactionManager txManager, GlobalNoticeReader globalNotices) {
        this.globalNotices = globalNotices;
        this.secretGate = secretGate;
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
        return list(rawId, userId, cursorRaw, sizeRaw, null);
    }

    /**
     * {@link #list(String, Long, String, String)} + 처리 상태 거르기(V168 {@code ?status=} — 소모임 게시판이 상태를 쓸 때만, 모르는 값이면 빈 목록, RECEIVED 는 값 없는 글도).
     */
    @Transactional(readOnly = true)
    public GroupPostPageView list(String rawId, Long userId, String cursorRaw, String sizeRaw, String status) {
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
        StatusFilter filter = statusFilter(b, status);
        List<GroupPostQueries.Row> found = filter.impossible() ? new ArrayList<>()
                : new ArrayList<>(postQueries.rows(group.getId(), member, filter.status(), cursor, size + 1, now));
        boolean hasNext = found.size() > size;
        List<GroupPostQueries.Row> page = hasNext ? found.subList(0, size) : found;
        List<GroupPostRowView> items = items(group, page, b, s, now, userId);
        String next = hasNext && !page.isEmpty() ? Long.toString(page.getLast().data().id()) : null;
        // 공개 소모임이면 잠금 줄·숨은 수를 만들지 않는다(비멤버에게는 비멤버에게 보이는 글만 — 멤버 전용으로 돌린 글은 세지 않는다, api-groups.md 11-2)
        boolean locked = !member && !group.isPublicGroup();
        return new GroupPostPageView(items, hasNext, next, locked, locked ? postQueries.privateCount(group.getId(), now) : 0);
    }

    /**
     * 글 탭 — 게시판 목록처럼 쪽 번호로(2026-11-06 사용자). 쪽 크기는 소모임 게시판의 목록 크기, 정렬 LATEST(기본)·OLDEST, 말머리·검색은 게시판 목록과 같다
     * (없는 말머리면 빈 쪽, 검색어는 다듬어 {@value PostRepositoryQDSL#QUERY_MAX}자까지). 보이는 글·잠금 줄은 {@link #list} 와 같다.
     *
     * @param pageNo 1부터(1보다 작으면 1)
     */
    @Transactional(readOnly = true)
    public GroupPostPageView page(String rawId, Long userId, int pageNo, String sort, String q, String prefix) {
        return page(rawId, userId, pageNo, sort, q, prefix, null);
    }

    /** {@link #page(String, Long, int, String, String, String)} + 처리 상태 거르기(V168 — {@link #list(String, Long, String, String, String)} 와 같은 규칙) */
    @Transactional(readOnly = true)
    public GroupPostPageView page(String rawId, Long userId, int pageNo, String sort, String q, String prefix, String status) {
        GroupEntity group = access.active(GroupAccess.parseId(rawId));
        boolean member = isMember(group, userId);
        Instant now = Instant.now();
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = s.board(group.getBoardId()).orElseThrow(GroupNotFoundException::new);
        int listSize = b.listSize();
        int current = Math.max(1, pageNo);
        Long prefixId = null;
        boolean impossible = false;
        if (prefix != null && !prefix.isBlank()) {
            prefixId = PostQueryService.parseId(prefix);
            impossible = prefixId == null || b.prefix(prefixId).isEmpty();
        }
        String query = ContentRules.normalize(q);
        if (query.length() > PostRepositoryQDSL.QUERY_MAX) {
            query = query.substring(0, PostRepositoryQDSL.QUERY_MAX);
        }
        StatusFilter filter = statusFilter(b, status);
        GroupPostQueries.Paged found = impossible || filter.impossible() ? new GroupPostQueries.Paged(List.of(), 0)
                : postQueries.page(group.getId(), member, prefixId, filter.status(), query.isEmpty() ? null : query, "OLDEST".equals(sort), (long) (current - 1) * listSize,
                listSize, now);
        int totalPages = (int) ((found.total() + listSize - 1) / listSize);
        boolean locked = !member && !group.isPublicGroup();
        // 전체 공지(V174) — 게시판 목록과 같은 조건(첫 쪽 · 말머리/검색어/상태 거르기 없음)
        boolean plain = !impossible && prefixId == null && query.isEmpty() && filter.status() == null && !filter.impossible();
        return new GroupPostPageView(items(group, found.rows(), b, s, now, userId), current < totalPages, null, locked, locked ? postQueries.privateCount(group.getId(), now) : 0,
                current, totalPages, found.total(), plain && current == 1 ? globalNotices.forBoard(b, now) : List.of());
    }

    /** 처리 상태 거르기 — 게시판이 상태를 쓰지 않거나 값이 비면 거르지 않는다, 모르는 값이면 빈 목록 */
    private record StatusFilter(PostProcessStatus status, boolean impossible) {
    }

    private static StatusFilter statusFilter(EffectiveBoard b, String status) {
        if (!b.statusEnabled() || status == null || status.isBlank()) {
            return new StatusFilter(null, false);
        }
        PostProcessStatus parsed = PostProcessStatus.parse(status).orElse(null);
        return new StatusFilter(parsed, parsed == null);
    }

    /**
     * 글 줄 — 게시판 글 줄에 소모임 공개 여부를 붙인다. 읽을 수 없는 비밀글(V168 — 글쓴이·소모임장·글 관리 매니저·운영자 회원 계정이 아닌 사람)은 줄·제목만 남기고 내용 칸을 비운다.
     */
    private List<GroupPostRowView> items(GroupEntity group, List<GroupPostQueries.Row> page, EffectiveBoard b, BoardCatalogSnapshot s, Instant now, Long userId) {
        java.util.Set<Long> sealed = new java.util.HashSet<>();
        boolean anySecret = page.stream().anyMatch(row -> row.data().secretPost());
        boolean staffReader = anySecret && secretGate.groupStaffReader(s, group, userId);
        if (anySecret && !staffReader) {
            page.stream().map(GroupPostQueries.Row::data).filter(d -> d.secretPost() && (userId == null || !userId.equals(d.authorId()))).forEach(d -> sealed.add(d.id()));
        }
        List<PostRowView> views = rows.boardRows(page.stream().map(GroupPostQueries.Row::data).toList(), b, s, now, userId, sealed);
        List<GroupPostRowView> items = new ArrayList<>(views.size());
        String groupId = Long.toString(group.getId());
        for (int i = 0; i < views.size(); i++) {
            items.add(new GroupPostRowView(views.get(i), PostGroupView.ref(groupId, group.getName(), page.get(i).publicPost(), false)));
        }
        return items;
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
            PostWriteService.GroupWrite result = writes.createInGroup(group.getId(), group.isPublicGroup(), b, actor, request, ip, s);
            group.touchActivity(Instant.now());
            return result;
        });
        writes.afterGroupWrite(me); // 커밋 뒤 — 활동 수 조건 즉시 승급(실패해도 글쓰기는 성공)
        log.info("소모임 글을 썼다 groupId={} postId={} userId={}", groupId, written.id(), me);
        return new GroupPostWriteResultView(Long.toString(written.id()), written.pointsEarned());
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
