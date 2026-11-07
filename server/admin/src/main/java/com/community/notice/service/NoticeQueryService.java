package com.community.notice.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.notice.dto.response.NoticeAuthorView;
import com.community.notice.dto.response.NoticeBarSummaryView;
import com.community.notice.dto.response.NoticeBoardView;
import com.community.notice.dto.response.NoticeContentView;
import com.community.notice.dto.response.NoticeContextView;
import com.community.notice.dto.response.NoticeDetailView;
import com.community.notice.dto.response.NoticeItemView;
import com.community.notice.dto.response.NoticeListView;
import com.community.notice.dto.response.NoticeWriterView;
import com.community.notice.exception.NoticeBoardMissingException;
import com.community.operator.repository.master.OperatorMemberAccountStore.Link;
import com.community.operator.service.OperatorMemberAccountService;
import com.community.permission.dto.AdminActor;
import com.community.post.dto.AdminPostListRow;
import com.community.post.dto.AdminPostSearch;
import com.community.post.dto.response.AdminPostPrefixRefView;
import com.community.post.entity.ContentFormat;
import com.community.post.entity.PostEntity;
import com.community.post.exception.AdminPostNotFoundException;
import com.community.post.repository.master.AdminPostRepositoryQDSL;
import com.community.post.repository.master.PostRepository;
import com.community.post.service.AdminPostQueryService;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.site.service.SiteNoticeBars;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * 공지 관리 읽기({@code post.read}) — 컨텍스트 · 목록 · 상세. docs/features/notice-admin-contract.md.
 *
 * <ul>
 *   <li><b>공지 게시판</b>: 게시판 사본({@link BoardCatalog})의 slug {@value SiteNoticeBars#NOTICE_BOARD_SLUG} 게시판 중 소모임 게시판이 아닌 것(공지 띠
 *       {@code SiteNoticeBarReader} 와 같은 판정 — 다만 꺼진 게시판도 찾는다: 목록 · 보기는 되고 쓰기만 409). 없으면 컨텍스트의 {@code board} 가 null, 목록 · 상세는 404 NOTICE_BOARD_MISSING.</li>
 *   <li><b>목록</b>: 게시글 관리 목록과 같은 거르기({@code AdminPostRepositoryQDSL}) — {@code q} 제목 부분 일치(대소문자 무시, 앞뒤 공백을 뗀 1자 이상, 50자에서 자른다) ·
 *       {@code state} ALL(기본) · PUBLIC · HIDDEN · DELETED(모르는 값은 ALL) · {@code prefix} 말머리 ID(깨진 ID 는 빈 결과) · {@code page} 0부터 · {@code size} 기본 20, 최대 50.
 *       정렬은 고정 글 먼저({@code pinnedAt} 내림차순), 그다음 최신.</li>
 *   <li><b>상세</b>: 공지 게시판 글이 아니면 404 POST_NOT_FOUND. 지운 글 본문도 보인다(파기 전까지).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class NoticeQueryService {

    static final String WRITER_LINKED = "LINKED";
    static final String WRITER_NOT_LINKED = "NOT_LINKED";
    static final String WRITER_PAUSED = "PAUSED";
    static final int SIZE_DEFAULT = 20;
    static final int SIZE_MAX = 50;
    static final int QUERY_MAX = 50;
    private static final String MEMBER = "MEMBER";
    private static final String GUEST = "GUEST";

    private final BoardCatalog catalog;
    private final AdminPostRepositoryQDSL posts;
    private final PostRepository postEntities;
    private final ProfileRepository profiles;
    private final UserRepository users;
    private final OperatorMemberAccountService accounts;
    private final AdminPostQueryService postQueries;
    private final SiteSettingRepository settings;

    public NoticeQueryService(BoardCatalog catalog, AdminPostRepositoryQDSL posts, PostRepository postEntities, ProfileRepository profiles, UserRepository users,
                              OperatorMemberAccountService accounts, AdminPostQueryService postQueries, SiteSettingRepository settings) {
        this.catalog = catalog;
        this.posts = posts;
        this.postEntities = postEntities;
        this.profiles = profiles;
        this.users = users;
        this.accounts = accounts;
        this.postQueries = postQueries;
        this.settings = settings;
    }

    // ── 공지 게시판 ─────────────────────────────────────────────────────────

    /** 공지 게시판(꺼진 것 포함) — 없거나 소모임 게시판이면 empty */
    public Optional<EffectiveBoard> noticeBoard() {
        return catalog.snapshot().board(SiteNoticeBars.NOTICE_BOARD_SLUG).filter(b -> !BoardFields.KIND_GROUP.equals(b.kind()));
    }

    /** 공지 게시판 — 없으면 404 NOTICE_BOARD_MISSING */
    public EffectiveBoard requireBoard() {
        return noticeBoard().orElseThrow(NoticeBoardMissingException::new);
    }

    // ── 컨텍스트 ────────────────────────────────────────────────────────────

    public NoticeContextView context(AdminActor actor) {
        Instant now = Instant.now();
        NoticeBoardView board = noticeBoard().map(b -> new NoticeBoardView(Long.toString(b.id()), b.slug(), b.name(), b.nameEn(), b.active(), b.pinnedMax(),
                posts.pinnedCount(b.id(), 0L, now), b.activePrefixes().stream().map(p -> new AdminPostPrefixRefView(Long.toString(p.id()), p.name())).toList(),
                postQueries.publicUrl("/boards/" + b.slug()))).orElse(null);
        return new NoticeContextView(board, writer(actor.operatorId()), noticeBar());
    }

    /** 로그인한 운영자의 운영자 회원 계정 상태(요청마다 DB 에서 판정) */
    NoticeWriterView writer(long operatorId) {
        Optional<Link> link = accounts.linkOfOperator(operatorId);
        if (link.isEmpty()) {
            return new NoticeWriterView(WRITER_NOT_LINKED, null, null, List.of());
        }
        Link l = link.get();
        return new NoticeWriterView(l.active() ? WRITER_LINKED : WRITER_PAUSED, Long.toString(l.userId()), l.nickname(),
                l.active() ? List.of() : OperatorMemberAccountService.pausedReasons(l));
    }

    private NoticeBarSummaryView noticeBar() {
        return settings.findById(SiteSettingEntity.ROW_ID)
                .map(row -> new NoticeBarSummaryView(row.isNoticeBarEnabled(), row.getNoticeBarSource(),
                        SiteNoticeBars.isBoardCount(row.getNoticeBarBoardCount()) ? row.getNoticeBarBoardCount() : SiteNoticeBars.DEFAULT_BOARD_COUNT))
                .orElse(new NoticeBarSummaryView(true, SiteNoticeBars.SOURCE_BOARD, SiteNoticeBars.DEFAULT_BOARD_COUNT));
    }

    // ── 목록 · 상세 ─────────────────────────────────────────────────────────

    public NoticeListView list(String q, String state, String prefix, String pageRaw, String sizeRaw) {
        EffectiveBoard board = requireBoard();
        Instant now = Instant.now();
        int page = Math.max(0, intOr(pageRaw, 0));
        int size = Math.min(SIZE_MAX, Math.max(1, intOr(sizeRaw, SIZE_DEFAULT)));
        String title = trim(q);
        if (title != null && title.codePointCount(0, title.length()) > QUERY_MAX) {
            title = title.substring(0, title.offsetByCodePoints(0, QUERY_MAX));
        }
        String st = trim(state) == null ? null : trim(state).toUpperCase(Locale.ROOT);
        String stateFilter = AdminPostListRow.PUBLIC.equals(st) || AdminPostListRow.HIDDEN.equals(st) || AdminPostListRow.DELETED.equals(st) ? st : null;
        Long prefixId = null;
        boolean none = false;
        if (trim(prefix) != null) {
            prefixId = AdminPostQueryService.parseId(prefix);
            none = prefixId == null;
        }
        AdminPostSearch search = new AdminPostSearch(null, title, null, null, null, board.id(), prefixId, stateFilter, false, none);
        AdminPostRepositoryQDSL.ListPage result = posts.searchPinnedFirst(search, (long) page * size, size, now);
        return new NoticeListView(result.total(), page, size, items(result.items(), now));
    }

    public NoticeDetailView detail(String idText) {
        EffectiveBoard board = requireBoard();
        Long id = AdminPostQueryService.parseId(idText);
        PostEntity post = id == null ? null : postEntities.findById(id).orElse(null);
        AdminPostListRow row = id == null ? null : posts.listRow(id);
        if (post == null || row == null || !Objects.equals(post.getBoardId(), board.id()) || post.isGroupPost()) {
            throw new AdminPostNotFoundException();
        }
        NoticeItemView item = items(List.of(row), Instant.now()).getFirst();
        return new NoticeDetailView(item, new NoticeContentView(ContentFormat.HTML.name(), html(post)));
    }

    /** 저장된 본문을 HTML 로 — 평문 글은 줄마다 문단(글자는 이스케이프), 파기한 글은 빈 글자 */
    private static String html(PostEntity post) {
        if (post.isPurged() || post.getContent() == null) {
            return "";
        }
        if (post.getContentFormat() == ContentFormat.HTML) {
            return post.getContent();
        }
        StringBuilder out = new StringBuilder();
        for (String line : post.getContent().split("\n", -1)) {
            out.append("<p>").append(HtmlUtils.htmlEscape(line)).append("</p>");
        }
        return out.toString();
    }

    private List<NoticeItemView> items(List<AdminPostListRow> rows, Instant now) {
        if (rows.isEmpty()) {
            return List.of();
        }
        BoardCatalogSnapshot s = catalog.snapshot();
        List<Long> ids = rows.stream().map(AdminPostListRow::id).toList();
        List<Long> authorIds = rows.stream().map(AdminPostListRow::authorId).filter(Objects::nonNull).distinct().toList();
        Map<Long, ProfileEntity> profileById = new HashMap<>();
        Map<Long, UserEntity> userById = new HashMap<>();
        Map<Long, Link> links = Map.of();
        if (!authorIds.isEmpty()) {
            profiles.findAllById(authorIds).forEach(p -> profileById.put(p.getUserId(), p));
            users.findAllById(authorIds).forEach(u -> userById.put(u.getId(), u));
            links = accounts.linksOfUsers(authorIds);
        }
        Map<Long, Instant> edited = posts.editedAt(ids);
        String base = postQueries.publicUrl("");
        List<NoticeItemView> out = new ArrayList<>(rows.size());
        for (AdminPostListRow r : rows) {
            EffectiveBoard b = r.boardId() == null ? null : s.board(r.boardId()).orElse(null);
            BoardPrefixInfo p = b == null ? null : b.prefix(r.prefixId()).orElse(null);
            AdminPostPrefixRefView prefix = p == null ? null : new AdminPostPrefixRefView(Long.toString(p.id()), p.name());
            NoticeAuthorView author;
            if (r.authorId() == null) {
                author = new NoticeAuthorView(GUEST, null, r.guestName() == null ? "" : r.guestName(), false, null);
            } else {
                ProfileEntity profile = profileById.get(r.authorId());
                UserEntity user = userById.get(r.authorId());
                Link link = links.get(r.authorId());
                author = new NoticeAuthorView(MEMBER, Long.toString(r.authorId()), profile == null ? "" : profile.getNickname(),
                        user == null || user.isGone() || profile == null, link == null ? null : link.operatorName());
            }
            String state = r.stateAt(now);
            boolean hidden = r.hiddenAt(now);
            Instant deletedAt = r.deletedAt() != null ? r.deletedAt()
                    : AdminPostListRow.DELETED.equals(state) && r.deleteScheduledAt() != null ? r.deleteScheduledAt() : null;
            out.add(new NoticeItemView(Long.toString(r.id()), prefix, r.title(), author, state, hidden ? r.hiddenReason() : null, hidden ? r.hiddenAt() : null,
                    hidden ? r.hiddenUntil() : null, deletedAt, r.deleteScheduledAt(), r.purgedAt() != null, r.pinnedAt() != null, r.pinnedAt(), n(r.viewCount()),
                    n(r.commentCount()), r.createdAt(), edited.get(r.id()), base == null ? null : base + "/posts/" + r.id()));
        }
        return out;
    }

    private static String trim(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip();
        return s.isEmpty() ? null : s;
    }

    private static int intOr(String raw, int fallback) {
        String s = trim(raw);
        if (s == null || s.length() > 9) {
            return fallback;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int n(Integer value) {
        return value == null ? 0 : value;
    }
}
