package com.community.post.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupStatus;
import com.community.group.repository.master.GroupRepository;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.poll.dto.PollData;
import com.community.poll.repository.master.PollQueries;
import com.community.post.dto.AdminPostListRow;
import com.community.post.dto.AdminPostSearch;
import com.community.post.dto.response.AdminPostAuthorView;
import com.community.post.dto.response.AdminPostBoardOptionView;
import com.community.post.dto.response.AdminPostBoardOptionsView;
import com.community.post.dto.response.AdminPostBoardRefView;
import com.community.post.dto.response.AdminPostContentView;
import com.community.post.dto.response.AdminPostDetailView;
import com.community.post.dto.response.AdminPostEventActorView;
import com.community.post.dto.response.AdminPostEventView;
import com.community.post.dto.response.AdminPostGroupView;
import com.community.post.dto.response.AdminPostItemView;
import com.community.post.dto.response.AdminPostListView;
import com.community.post.dto.response.AdminPostPrefixOptionView;
import com.community.post.dto.response.AdminPostPrefixRefView;
import com.community.post.dto.response.AdminPollOptionView;
import com.community.post.dto.response.AdminPollView;
import com.community.post.entity.PostEntity;
import com.community.post.entity.PostModerationEventEntity;
import com.community.post.exception.AdminPostNotFoundException;
import com.community.post.repository.master.AdminPostRepositoryQDSL;
import com.community.post.repository.master.PostModerationEventRepository;
import com.community.post.repository.master.PostRepository;
import com.community.site.dto.SiteValues;
import com.community.site.service.SiteSettingsService;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 게시글 관리 읽기({@code post.read}) — 목록 · 게시판 고르기 · 글 상세(본문 · 조치 기록). docs/features/post-admin-contract.md.
 *
 * <ul>
 *   <li><b>검색</b>: {@code searchField} 로 고른 한 칸만 — {@code TITLE}(기본) 제목 · {@code AUTHOR} 작성자 닉네임(비회원 이름) 부분 일치(대소문자 무시, 앞뒤 공백을 뗀 2자 이상 —
 *       한 글자는 무시, 50자에서 자른다) · {@code ID} 글 번호 정확히 일치(앞의 {@code #} 허용, 숫자가 아니면 {@code 400 fields.q}). 본문 검색은 하지 않는다(성능).
 *       모르는 {@code searchField} 는 {@code 400 fields.searchField}.</li>
 *   <li><b>작성 기간</b>: {@code createdFrom}·{@code createdTo} = UTC ISO 순간(양 끝 포함, 화면이 사이트 시간대 하루 시작·끝을 바꿔 보낸다). 형식 오류는 그 칸 400,
 *       시작 &gt; 끝은 {@code 400 fields.createdTo}.</li>
 *   <li><b>거르기</b>: {@code board} = 게시판 ID, {@code prefix} = 말머리 ID(게시판을 고른 뒤 — 게시판 없이 오면 무시). 깨진 ID 는 「아무 글도 안 맞음」.
 *       모르는 {@code state}·{@code sort} 는 기본값(ALL · newest). {@code page} 는 0 부터, {@code size} 기본 20 · 최대 50.</li>
 *   <li><b>지운 글 본문</b>: 파기 전까지 {@code post.read} 이면 누구나 본다(community Q-5 ① 과 같다). 읽기는 감사 기록에 남기지 않는다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AdminPostQueryService {

    private static final Logger log = LoggerFactory.getLogger(AdminPostQueryService.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    static final int SIZE_DEFAULT = 20;
    static final int SIZE_MAX = 50;
    static final int QUERY_MIN = 2;
    static final int QUERY_MAX = 50;
    /** 글 번호 최대 자릿수(Snowflake, long) */
    static final int ID_DIGITS_MAX = 19;
    /** 검색 기준 */
    static final String FIELD_TITLE = "TITLE";
    static final String FIELD_AUTHOR = "AUTHOR";
    static final String FIELD_ID = "ID";
    /** 작성 기간으로 받는 순간의 범위(1~9999년) */
    private static final Instant INSTANT_MIN = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant INSTANT_MAX = Instant.parse("9999-12-31T23:59:59.999999999Z");

    static final String MEMBER = "MEMBER";
    static final String GUEST = "GUEST";
    static final String NOTICE_KEY = "NOTICE";
    /** 상세 {@code contentBlocked} — 비공개 소모임 글(본문·투표를 싣지 않는다) */
    static final String BLOCKED_GROUP_PRIVATE = "GROUP_PRIVATE";

    private final AdminPostRepositoryQDSL posts;
    private final PostRepository postEntities;
    private final PostModerationEventRepository events;
    private final ProfileRepository profiles;
    private final UserRepository users;
    private final OperatorRepository operators;
    private final BoardCatalog catalog;
    private final SiteSettingsService site;
    private final PollQueries polls;
    private final GroupRepository groupRepository;

    public AdminPostQueryService(AdminPostRepositoryQDSL posts, PostRepository postEntities, PostModerationEventRepository events, ProfileRepository profiles,
                                 UserRepository users, OperatorRepository operators, BoardCatalog catalog, SiteSettingsService site, PollQueries polls,
                                 GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
        this.polls = polls;
        this.site = site;
        this.posts = posts;
        this.postEntities = postEntities;
        this.events = events;
        this.profiles = profiles;
        this.users = users;
        this.operators = operators;
        this.catalog = catalog;
    }

    /** 목록 한 쪽(상태별 건수는 세지 않는다 — 사용자 결정 2026-10-24) */
    public AdminPostListView list(String searchField, String q, String createdFrom, String createdTo, String board, String prefix, String state, String sort,
                                  String pageRaw, String sizeRaw) {
        Instant now = Instant.now();
        int page = Math.max(0, intOr(pageRaw, 0));
        int size = Math.min(SIZE_MAX, Math.max(1, intOr(sizeRaw, SIZE_DEFAULT)));
        AdminPostSearch search = criteria(searchField, q, createdFrom, createdTo, board, prefix, state, sort);
        AdminPostRepositoryQDSL.ListPage result = posts.search(search, (long) page * size, size, now);
        return new AdminPostListView(result.total(), page, size,
                items(result.items(), now));
    }

    /** 게시판 고르기(거르기 · 이동용) — 켜진 게시판과 켜진 말머리만, 정렬 순서 */
    public AdminPostBoardOptionsView boardOptions() {
        BoardCatalogSnapshot s = catalog.snapshot();
        List<AdminPostBoardOptionView> boards = s.boards().stream().filter(EffectiveBoard::active)
                .map(b -> new AdminPostBoardOptionView(Long.toString(b.id()), b.slug(), b.name(), b.nameEn(), b.kind(), b.pinnedMax(),
                        b.activePrefixes().stream().map(p -> new AdminPostPrefixOptionView(Long.toString(p.id()), p.name(), p.staffOnly(), p.common())).toList()))
                .toList();
        return new AdminPostBoardOptionsView(boards);
    }

    /** 글 상세 — 없는 글 404 POST_NOT_FOUND */
    public AdminPostDetailView detail(String idText) {
        Long id = parseId(idText);
        PostEntity post = id == null ? null : postEntities.findById(id).orElse(null);
        AdminPostListRow row = id == null ? null : posts.listRow(id);
        if (post == null || row == null) {
            throw new AdminPostNotFoundException();
        }
        Instant now = Instant.now();
        AdminPostItemView item = items(List.of(row), now).get(0);
        // 비공개 소모임 글은 본문을 싣지 않는다(contentBlocked GROUP_PRIVATE — 내용은 신고 처리의 신고 당시 내용으로만, 소모임 2단계)
        boolean blocked = row.restricted();
        AdminPostContentView content = new AdminPostContentView(post.getContentFormat().name(), post.isPurged() || blocked ? null : post.getContent(),
                post.getEditedAt());
        return new AdminPostDetailView(item, content, eventViews(events.findByPostIdOrderByIdDesc(id)), publicUrlOf(id), blocked ? null : pollOf(id, now),
                blocked ? BLOCKED_GROUP_PRIVATE : null);
    }

    /** 글 한 줄(조치 서비스가 결과를 돌려줄 때도 쓴다) */
    List<AdminPostItemView> items(List<AdminPostListRow> rows, Instant now) {
        if (rows.isEmpty()) {
            return List.of();
        }
        BoardCatalogSnapshot s = catalog.snapshot();
        List<Long> ids = rows.stream().map(AdminPostListRow::id).toList();
        List<Long> authorIds = rows.stream().map(AdminPostListRow::authorId).filter(Objects::nonNull).distinct().toList();
        Map<Long, ProfileEntity> profileById = new HashMap<>();
        Map<Long, UserEntity> userById = new HashMap<>();
        if (!authorIds.isEmpty()) {
            profiles.findAllById(authorIds).forEach(p -> profileById.put(p.getUserId(), p));
            users.findAllById(authorIds).forEach(u -> userById.put(u.getId(), u));
        }
        Map<Long, Integer> reports = posts.openReports(ids);
        Set<Long> debates = posts.debateOpen(ids);
        Set<Long> withPoll = polls.postIdsWithPoll(ids);
        Map<Long, Integer> opinions = posts.opinionCounts(ids);
        Map<Long, Integer> pinRequests = posts.pendingPinRequests(ids);
        Map<Long, GroupEntity> groupById = new HashMap<>();
        List<Long> groupIds = rows.stream().map(AdminPostListRow::groupId).filter(Objects::nonNull).distinct().toList();
        if (!groupIds.isEmpty()) {
            groupRepository.findAllById(groupIds).forEach(g -> groupById.put(g.getId(), g));
        }
        List<AdminPostItemView> out = new ArrayList<>(rows.size());
        for (AdminPostListRow r : rows) {
            out.add(item(r, s, profileById, userById, reports, debates, withPoll, opinions, pinRequests, groupById, now));
        }
        return out;
    }

    private static AdminPostItemView item(AdminPostListRow r, BoardCatalogSnapshot s, Map<Long, ProfileEntity> profileById, Map<Long, UserEntity> userById,
                                          Map<Long, Integer> reports, Set<Long> debates, Set<Long> withPoll, Map<Long, Integer> opinions,
                                          Map<Long, Integer> pinRequests, Map<Long, GroupEntity> groupById, Instant now) {
        EffectiveBoard b = r.boardId() == null ? null : s.board(r.boardId()).orElse(null);
        AdminPostBoardRefView board = new AdminPostBoardRefView(r.boardId() == null ? null : Long.toString(r.boardId()), b == null ? null : b.name(),
                b == null ? null : b.nameEn(), b == null ? null : b.slug());
        BoardPrefixInfo p = b == null ? null : b.prefix(r.prefixId()).orElse(null);
        AdminPostPrefixRefView prefix = p == null ? null : new AdminPostPrefixRefView(Long.toString(p.id()), p.name());

        AdminPostAuthorView author;
        if (r.authorId() == null) {
            author = new AdminPostAuthorView(GUEST, null, r.guestName() == null ? "" : r.guestName(), false);
        } else {
            ProfileEntity profile = profileById.get(r.authorId());
            UserEntity user = userById.get(r.authorId());
            boolean withdrawn = user == null || user.isGone() || profile == null;
            author = new AdminPostAuthorView(MEMBER, Long.toString(r.authorId()), profile == null ? "" : profile.getNickname(), withdrawn);
        }

        String state = r.stateAt(now);
        boolean hidden = r.hiddenAt(now);
        Instant deletedAt = r.deletedAt() != null ? r.deletedAt()
                : AdminPostListRow.DELETED.equals(state) && r.deleteScheduledAt() != null ? r.deleteScheduledAt() : null;
        AdminPostGroupView group = null;
        if (r.groupId() != null) {
            GroupEntity g = groupById.get(r.groupId());
            group = new AdminPostGroupView(Long.toString(r.groupId()), g == null ? null : g.getName(), r.restricted() ? "PRIVATE" : "PUBLIC",
                    g != null && g.getStatus() == GroupStatus.DELETED, r.groupLeftAt() != null);
        }
        return new AdminPostItemView(Long.toString(r.id()), board, prefix, r.restricted() ? null : r.title(), author, state, hidden ? r.hiddenReason() : null,
                hidden ? r.hiddenAt() : null, hidden ? r.hiddenUntil() : null, deletedAt, r.deleteScheduledAt(), r.purgedAt() != null, r.pinnedAt() != null,
                p != null && NOTICE_KEY.equals(p.commonKey()), Boolean.TRUE.equals(r.adult()), debates.contains(r.id()), withPoll.contains(r.id()), n(r.likeCount()),
                n(r.commentCount()), n(r.viewCount()), reports.getOrDefault(r.id(), 0), r.createdAt(), opinions.getOrDefault(r.id(), 0),
                pinRequests.getOrDefault(r.id(), 0), group, r.restricted());
    }

    private List<AdminPostEventView> eventViews(List<PostModerationEventEntity> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Collection<Long> operatorIds = rows.stream().map(PostModerationEventEntity::getActorId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        if (!operatorIds.isEmpty()) {
            operators.findAllById(operatorIds).forEach(o -> names.put(o.getId(), operatorName(o)));
        }
        List<AdminPostEventView> out = new ArrayList<>(rows.size());
        for (PostModerationEventEntity e : rows) {
            AdminPostEventActorView actor = new AdminPostEventActorView(e.getActorKind(), e.getActorId() == null ? null : Long.toString(e.getActorId()),
                    e.getActorId() == null ? null : names.get(e.getActorId()));
            out.add(new AdminPostEventView(Long.toString(e.getId()), e.getType(), actor, e.getReason(), detailOf(e.getDetail()), e.getCreatedAt()));
        }
        return out;
    }

    /** 글의 투표 — 집계만(참여 수·선택지별 득표, 마감 전에도), 누가 골랐는지는 없다. 투표가 없으면 null */
    private AdminPollView pollOf(long postId, Instant now) {
        PollData poll = polls.find(postId).orElse(null);
        if (poll == null) {
            return null;
        }
        Map<Long, Integer> counts = polls.votesByOption(poll.id());
        List<AdminPollOptionView> options = polls.options(poll.id()).stream()
                .map(o -> new AdminPollOptionView(Long.toString(o.id()), o.label(), counts.getOrDefault(o.id(), 0))).toList();
        return new AdminPollView(Long.toString(poll.id()), poll.question(), poll.multiple(), poll.changeAllowed(), poll.closesAt(), poll.closedAt(now),
                polls.voterCount(poll.id()), options, poll.createdAt());
    }

    /** 공개 사이트의 글 주소 —사이트 설정 공개 주소(소셜 로그인·메일 링크와 같은 값)가 없으면 null */
    private String publicUrlOf(long postId) {
        return site.current().map(SiteValues::publicUrl).filter(url -> url != null && !url.isBlank())
                .map(url -> url.strip().replaceAll("/+$", "") + "/posts/" + postId).orElse(null);
    }

    private static String operatorName(OperatorEntity operator) {
        return operator.getOperatorName();
    }

    private static Map<String, Object> detailOf(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (RuntimeException e) {
            log.warn("글 조치 기록의 덧붙임을 읽지 못해 비운다 원인={}", e.getClass().getSimpleName());
            return null;
        }
    }

    // ── 요청 값 풀기 ──────────────────────────────────────────────────────

    private AdminPostSearch criteria(String searchField, String q, String createdFrom, String createdTo, String board, String prefix, String state, String sort) {
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        String field = trim(searchField) == null ? FIELD_TITLE : trim(searchField).toUpperCase(Locale.ROOT);
        if (!FIELD_TITLE.equals(field) && !FIELD_AUTHOR.equals(field) && !FIELD_ID.equals(field)) {
            invalid.put("searchField", new MessageRef("post.admin.query.searchField.invalid"));
        }
        Instant from = instant("createdFrom", createdFrom, invalid);
        Instant to = instant("createdTo", createdTo, invalid);

        Long postId = null;
        String title = null;
        String author = null;
        boolean noPost = false;
        String query = trim(q);
        if (query != null && FIELD_ID.equals(field)) {
            String digits = query.startsWith("#") ? query.substring(1).strip() : query;
            if (digits.isEmpty() || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
                invalid.put("q", new MessageRef("post.admin.query.id.invalid"));
            } else {
                postId = parseId(digits);
                noPost = postId == null; // 0·자릿수 넘침 — 그런 글은 없다
            }
        } else if (query != null && query.codePointCount(0, query.length()) >= QUERY_MIN) {
            String text = query.codePointCount(0, query.length()) <= QUERY_MAX ? query : query.substring(0, query.offsetByCodePoints(0, QUERY_MAX));
            if (FIELD_AUTHOR.equals(field)) {
                author = text;
            } else {
                title = text;
            }
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw ValidationException.of("createdTo", new MessageRef("post.admin.query.period.reversed"));
        }

        boolean oldest = "oldest".equalsIgnoreCase(trim(sort));
        String st = trim(state) == null ? null : trim(state).toUpperCase(Locale.ROOT);
        String stateFilter = AdminPostListRow.PUBLIC.equals(st) || AdminPostListRow.HIDDEN.equals(st) || AdminPostListRow.DELETED.equals(st) ? st : null;
        boolean none = false;
        Long boardId = null;
        Long prefixId = null;
        if (trim(board) != null) {
            boardId = parseId(board);
            none = boardId == null;
            if (boardId != null && trim(prefix) != null) {
                prefixId = parseId(prefix);
                none = prefixId == null;
            }
        }
        return new AdminPostSearch(postId, title, author, from, to, boardId, prefixId, stateFilter, oldest, none || noPost);
    }

    /** UTC ISO 순간(회원 관리 {@code joinedFrom}·{@code joinedTo} 와 같은 규칙) — 비었으면 null, 형식이 틀리면 칸 오류 */
    private static Instant instant(String field, String raw, Map<String, MessageRef> invalid) {
        String s = trim(raw);
        if (s == null) {
            return null;
        }
        try {
            Instant at = Instant.parse(s);
            if (at.isBefore(INSTANT_MIN) || at.isAfter(INSTANT_MAX)) {
                throw new DateTimeParseException("out of range", s, 0); // 밀리초 계산이 넘치지 않게
            }
            return at;
        } catch (DateTimeParseException e) {
            invalid.put(field, new MessageRef("post.admin.query.date.invalid"));
            return null;
        }
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

    /** 문자열 ID → long. 형식이 틀리면 null */
    static Long parseId(String raw) {
        if (raw == null || raw.isBlank() || raw.strip().length() > ID_DIGITS_MAX) {
            return null;
        }
        try {
            long id = Long.parseLong(raw.strip());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
