package com.community.report.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.comment.entity.CommentEntity;
import com.community.comment.entity.CommentId;
import com.community.comment.repository.master.CommentRepository;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.post.entity.ContentFormat;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostRepository;
import com.community.report.dto.response.ReportAuthorCardView;
import com.community.report.dto.response.ReportAuthorView;
import com.community.report.dto.response.ReportBoardRefView;
import com.community.report.dto.response.ReportCaseDetailView;
import com.community.report.dto.response.ReportCaseItemView;
import com.community.report.dto.response.ReportCaseListView;
import com.community.report.dto.response.ReportClosedCasesView;
import com.community.report.dto.response.ReportContentView;
import com.community.report.dto.response.ReportEventActorView;
import com.community.report.dto.response.ReportEventView;
import com.community.report.dto.response.ReportLineView;
import com.community.report.dto.response.ReportOperatorRefView;
import com.community.report.dto.response.ReportReasonCountView;
import com.community.report.dto.response.ReportReporterView;
import com.community.report.dto.response.ReportSnapshotView;
import com.community.report.dto.response.ReportSummaryView;
import com.community.report.dto.response.ReportTargetView;
import com.community.report.entity.ReportCaseEntity;
import com.community.report.entity.ReportEntity;
import com.community.report.entity.ReportEventEntity;
import com.community.report.entity.ReportReasons;
import com.community.report.exception.ReportCaseNotFoundException;
import com.community.report.repository.master.AdminReportRepositoryQDSL;
import com.community.report.repository.master.ReportCaseRepository;
import com.community.report.repository.master.ReportEventRepository;
import com.community.report.repository.master.ReportRepositoryQDSL;
import com.community.site.dto.SiteValues;
import com.community.site.service.SiteSettingsService;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 신고 관리 읽기({@code report.read}) — 사건 목록 · 열린 사건 요약 · 사건 상세. docs/features/report-admin-contract.md.
 *
 * <ul>
 *   <li><b>목록 거르기</b>: {@code status} OPEN(기본 — 대기+검토 중) · PENDING · IN_REVIEW · RESOLVED · DISMISSED · ALL(모르는 값은 OPEN) · {@code targetType} POST · COMMENT(모르는 값은 무시)
 *       · {@code reason} 신고 사유 코드(그 사유 신고가 하나라도 있는 사건, 모르는 값은 무시) · {@code board} 게시판 ID · {@code postId} 그 글과 그 글의 댓글 사건(깨진 ID 는 「아무것도 안 맞음」).</li>
 *   <li><b>검색</b>: {@code searchField} TITLE(기본 — 대상 글 제목, 댓글 사건은 그 댓글의 글) · AUTHOR(대상 작성자 닉네임 · 비회원 이름) — 앞뒤 공백을 뗀 2자 이상(한 글자는 무시, 50자에서 자른다)
 *       · CASE_NO(사건 번호 정확히, 앞의 {@code #} 허용, 숫자가 아니면 {@code 400 fields.q}). 모르는 {@code searchField} 는 {@code 400 fields.searchField}.</li>
 *   <li><b>기간</b>: {@code from}·{@code to} = 마지막 신고 시각 UTC ISO 순간(양 끝 포함 — 게시글 관리와 같은 규칙). 형식 오류는 그 칸 400, 시작 &gt; 끝은 {@code 400 fields.to}.</li>
 *   <li><b>정렬</b>: {@code newest}(마지막 신고 최신, 기본) · {@code oldest} · {@code most}(신고 많은 순). {@code page} 0부터, {@code size} 기본 20 · 최대 50.</li>
 *   <li><b>상세</b>: 처리 권한({@code report.manage})이 있는 운영자가 처리 대기 사건을 열면 검토 중(검토자 = 나, 기록 REVIEW_STARTED)이 된다 — 읽기 권한만 있으면 바꾸지 않는다.
 *       신고자는 운영자에게만. 읽기는 감사 기록에 남기지 않는다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AdminReportQueryService {

    private static final Logger log = LoggerFactory.getLogger(AdminReportQueryService.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    static final int SIZE_DEFAULT = 20;
    static final int SIZE_MAX = 50;
    static final int QUERY_MIN = 2;
    static final int QUERY_MAX = 50;
    static final int ID_DIGITS_MAX = 19;
    /** 목록 발췌 글자 */
    static final int EXCERPT_MAX = 80;
    /** 작성자 카드의 닫힌 사건 기간 */
    static final Duration AUTHOR_WINDOW = Duration.ofDays(90);

    static final String STATUS_OPEN = "OPEN";
    static final String STATUS_ALL = "ALL";
    static final String FIELD_TITLE = "TITLE";
    static final String FIELD_AUTHOR = "AUTHOR";
    static final String FIELD_CASE_NO = "CASE_NO";

    static final String PUBLIC = "PUBLIC";
    static final String HIDDEN = "HIDDEN";
    static final String DELETED = "DELETED";
    static final String MEMBER = "MEMBER";
    static final String GUEST = "GUEST";

    private static final Instant INSTANT_MIN = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant INSTANT_MAX = Instant.parse("9999-12-31T23:59:59.999999999Z");

    private final AdminReportRepositoryQDSL search;
    private final ReportCaseRepository cases;
    private final ReportRepositoryQDSL reports;
    private final ReportEventRepository events;
    private final ReportCaseLog caseLog;
    private final PostRepository posts;
    private final CommentRepository comments;
    private final ProfileRepository profiles;
    private final UserRepository users;
    private final OperatorRepository operators;
    private final BoardCatalog catalog;
    private final SiteSettingsService site;

    public AdminReportQueryService(AdminReportRepositoryQDSL search, ReportCaseRepository cases, ReportRepositoryQDSL reports, ReportEventRepository events,
                                   ReportCaseLog caseLog, PostRepository posts, CommentRepository comments, ProfileRepository profiles, UserRepository users,
                                   OperatorRepository operators, BoardCatalog catalog, SiteSettingsService site) {
        this.search = search;
        this.cases = cases;
        this.reports = reports;
        this.events = events;
        this.caseLog = caseLog;
        this.posts = posts;
        this.comments = comments;
        this.profiles = profiles;
        this.users = users;
        this.operators = operators;
        this.catalog = catalog;
        this.site = site;
    }

    // ── 목록 · 요약 ───────────────────────────────────────────────────────

    public ReportCaseListView list(String status, String targetType, String reason, String searchField, String q, String from, String to, String board,
                                   String postId, String sort, String pageRaw, String sizeRaw) {
        int page = Math.max(0, intOr(pageRaw, 0));
        int size = Math.min(SIZE_MAX, Math.max(1, intOr(sizeRaw, SIZE_DEFAULT)));
        AdminReportRepositoryQDSL.Search criteria = criteria(status, targetType, reason, searchField, q, from, to, board, postId, sort);
        AdminReportRepositoryQDSL.ListPage result = search.search(criteria, (long) page * size, size);
        return new ReportCaseListView(result.total(), page, size, items(result.items(), Instant.now()));
    }

    public ReportSummaryView summary() {
        long[] counts = search.openCounts();
        return new ReportSummaryView(counts[0], counts[1]);
    }

    // ── 상세 ──────────────────────────────────────────────────────────────

    /**
     * 서랍을 열 때의 상세 — 처리 권한이 있고 대기 사건이면 검토 중으로 바꾼다(사건 행 잠금 — 두 운영자가 동시에 열어도 처음 연 사람이 검토자).
     */
    @Transactional
    public ReportCaseDetailView open(AdminActor actor, String idText) {
        Long id = parseId(idText);
        if (id == null) {
            throw new ReportCaseNotFoundException();
        }
        if (actor.can(Permission.REPORT_MANAGE)) {
            ReportCaseEntity locked = cases.findWithLockById(id).orElseThrow(ReportCaseNotFoundException::new);
            Instant now = Instant.now();
            if (locked.startReview(actor.operatorId(), now)) {
                caseLog.operator(locked.getId(), ReportEventEntity.REVIEW_STARTED, actor.operatorId(), null, now);
                cases.flush();
            }
        }
        return detail(idText);
    }

    /** 상세(바꾸지 않는다) — 조치·무시·다시 열기의 응답 */
    public ReportCaseDetailView detail(String idText) {
        Long id = parseId(idText);
        ReportCaseEntity c = id == null ? null : cases.findById(id).orElse(null);
        if (c == null) {
            throw new ReportCaseNotFoundException();
        }
        Instant now = Instant.now();
        Targets targets = targets(List.of(c));
        ReportCaseItemView item = item(c, targets, catalog.snapshot(), names(List.of(c)), reports.reasonsByCase(List.of(c.getId())), now);

        PostEntity post = targets.posts().get(c.getPostId());
        CommentEntity comment = c.isComment() ? targets.comments().get(c.getTargetId()) : null;
        ReportEntity first = reports.firstOfCase(c.getId());
        ReportSnapshotView snapshot = first == null ? null : new ReportSnapshotView(first.getSnapshotTitle(), first.getSnapshotContent(), first.getSnapshotAuthorName());
        ReportContentView content;
        if (c.isComment()) {
            content = comment == null ? new ReportContentView(ContentFormat.TEXT.name(), null, snapshot)
                    : new ReportContentView(comment.getContentFormat().name(), comment.getPurgedAt() != null ? null : comment.getContent(), snapshot);
        } else {
            content = post == null ? new ReportContentView(ContentFormat.TEXT.name(), null, snapshot)
                    : new ReportContentView(post.getContentFormat().name(), post.isPurged() ? null : post.getContent(), snapshot);
        }
        return new ReportCaseDetailView(item, content, lines(reports.ofCase(c.getId())), authorCard(c, post, comment, first, now),
                eventViews(events.findByCaseIdOrderByIdAsc(c.getId())), publicUrlOf(c.getPostId()));
    }

    // ── 한 줄 만들기 ──────────────────────────────────────────────────────

    /** 사건의 대상 글·댓글(지운 것 포함) */
    private record Targets(Map<Long, PostEntity> posts, Map<Long, CommentEntity> comments) {
    }

    private Targets targets(List<ReportCaseEntity> rows) {
        List<Long> postIds = rows.stream().map(ReportCaseEntity::getPostId).distinct().toList();
        Map<Long, PostEntity> postById = postIds.isEmpty() ? Map.of()
                : posts.findAllById(postIds).stream().collect(Collectors.toMap(PostEntity::getId, Function.identity(), (a, b) -> a));
        List<CommentId> commentIds = rows.stream().filter(ReportCaseEntity::isComment).map(r -> new CommentId(r.getPostId(), r.getTargetId())).distinct().toList();
        Map<Long, CommentEntity> commentById = commentIds.isEmpty() ? Map.of()
                : comments.findAllById(commentIds).stream().collect(Collectors.toMap(CommentEntity::getId, Function.identity(), (a, b) -> a));
        return new Targets(postById, commentById);
    }

    /** 작성자·검토자·처리자 이름 */
    private record Names(Map<Long, ProfileEntity> profiles, Map<Long, UserEntity> users, Map<Long, String> operators) {
    }

    private Names names(List<ReportCaseEntity> rows) {
        List<Long> authorIds = rows.stream().map(ReportCaseEntity::getTargetAuthorId).filter(Objects::nonNull).distinct().toList();
        Map<Long, ProfileEntity> profileById = new HashMap<>();
        Map<Long, UserEntity> userById = new HashMap<>();
        if (!authorIds.isEmpty()) {
            profiles.findAllById(authorIds).forEach(p -> profileById.put(p.getUserId(), p));
            users.findAllById(authorIds).forEach(u -> userById.put(u.getId(), u));
        }
        List<Long> operatorIds = rows.stream().flatMap(r -> java.util.stream.Stream.of(r.getReviewerId(), r.getClosedBy())).filter(Objects::nonNull).distinct()
                .toList();
        return new Names(profileById, userById, operatorNames(operatorIds));
    }

    private Map<Long, String> operatorNames(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        if (!ids.isEmpty()) {
            operators.findAllById(ids).forEach(o -> out.put(o.getId(), operatorName(o)));
        }
        return out;
    }

    List<ReportCaseItemView> items(List<ReportCaseEntity> rows, Instant now) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Targets targets = targets(rows);
        Names names = names(rows);
        Map<Long, Map<String, Long>> reasons = reports.reasonsByCase(rows.stream().map(ReportCaseEntity::getId).toList());
        BoardCatalogSnapshot s = catalog.snapshot();
        List<ReportCaseItemView> out = new ArrayList<>(rows.size());
        for (ReportCaseEntity c : rows) {
            out.add(item(c, targets, s, names, reasons, now));
        }
        return out;
    }

    private ReportCaseItemView item(ReportCaseEntity c, Targets targets, BoardCatalogSnapshot s, Names names, Map<Long, Map<String, Long>> reasons, Instant now) {
        PostEntity post = targets.posts().get(c.getPostId());
        ReportTargetView target;
        String guestName;
        if (c.isComment()) {
            CommentEntity comment = targets.comments().get(c.getTargetId());
            guestName = comment == null ? null : comment.getGuestName();
            target = new ReportTargetView(Long.toString(c.getPostId()), Long.toString(c.getTargetId()), postTitle(post),
                    comment == null || comment.getPurgedAt() != null ? null : excerpt(plain(comment.getContentFormat(), comment.getContent())),
                    commentState(comment, now), comment == null ? null : comment.hiddenReasonAt(now),
                    comment == null || !comment.isHiddenAt(now) ? null : comment.getHiddenUntil());
        } else {
            guestName = post == null ? null : post.getGuestName();
            boolean hidden = post != null && post.isHiddenAt(now);
            target = new ReportTargetView(Long.toString(c.getPostId()), null, postTitle(post),
                    post == null || post.isPurged() ? null : excerpt(plain(post.getContentFormat(), post.plainContent())), postState(post, now),
                    hidden ? post.getHiddenReason() : null, hidden ? post.getHiddenUntil() : null);
        }
        EffectiveBoard b = s.board(c.getBoardId()).orElse(null);
        ReportBoardRefView board = new ReportBoardRefView(Long.toString(c.getBoardId()), b == null ? null : b.name());

        ReportAuthorView author;
        if (c.getTargetAuthorId() == null) {
            author = new ReportAuthorView(GUEST, null, guestName == null ? "" : guestName, false);
        } else {
            ProfileEntity profile = names.profiles().get(c.getTargetAuthorId());
            UserEntity user = names.users().get(c.getTargetAuthorId());
            boolean withdrawn = user == null || user.isGone() || profile == null;
            author = new ReportAuthorView(MEMBER, Long.toString(c.getTargetAuthorId()), profile == null ? "" : profile.getNickname(), withdrawn);
        }

        Map<String, Long> byReason = reasons.getOrDefault(c.getId(), Map.of());
        List<ReportReasonCountView> reasonViews = ReportReasons.ALL.stream().filter(byReason::containsKey)
                .map(code -> new ReportReasonCountView(code, byReason.get(code))).toList();
        return new ReportCaseItemView(Long.toString(c.getId()), c.getCaseNo() == null ? 0 : c.getCaseNo(), c.getStatus(), c.getTargetType(), target, board, author,
                c.getReportCount(), reasonViews, c.getFirstReportedAt(), c.getLastReportedAt(), operatorRef(c.getReviewerId(), names.operators()), c.getClosedAt(),
                operatorRef(c.getClosedBy(), names.operators()), c.getAction(), c.getReasonCode(), c.getNote(), c.isFalseReport());
    }

    private static ReportOperatorRefView operatorRef(Long id, Map<Long, String> names) {
        return id == null ? null : new ReportOperatorRefView(Long.toString(id), names.get(id));
    }

    /** 글 제목 — 원문을 파기했거나 글이 없으면 null(화면은 스냅샷을 쓴다) */
    private static String postTitle(PostEntity post) {
        return post == null || post.isPurged() ? null : post.getTitle();
    }

    private static String postState(PostEntity post, Instant now) {
        if (post == null || post.isGoneAt(now)) {
            return DELETED;
        }
        return post.isHiddenAt(now) ? HIDDEN : PUBLIC;
    }

    private static String commentState(CommentEntity comment, Instant now) {
        if (comment == null || comment.isDeleted()) {
            return DELETED;
        }
        return comment.isHiddenAt(now) ? HIDDEN : PUBLIC;
    }

    private List<ReportLineView> lines(List<ReportEntity> rows) {
        List<Long> reporterIds = rows.stream().map(ReportEntity::getReporterId).distinct().toList();
        Map<Long, ProfileEntity> profileById = new HashMap<>();
        Map<Long, UserEntity> userById = new HashMap<>();
        if (!reporterIds.isEmpty()) {
            profiles.findAllById(reporterIds).forEach(p -> profileById.put(p.getUserId(), p));
            users.findAllById(reporterIds).forEach(u -> userById.put(u.getId(), u));
        }
        List<ReportLineView> out = new ArrayList<>(rows.size());
        for (ReportEntity r : rows) {
            ProfileEntity profile = profileById.get(r.getReporterId());
            UserEntity user = userById.get(r.getReporterId());
            boolean withdrawn = user == null || user.isGone() || profile == null;
            out.add(new ReportLineView(Long.toString(r.getId()), new ReportReporterView(Long.toString(r.getReporterId()), profile == null ? "" : profile.getNickname(),
                    withdrawn), r.getReason(), r.getDetail(), r.getCreatedAt(), r.getStatus()));
        }
        return out;
    }

    private ReportAuthorCardView authorCard(ReportCaseEntity c, PostEntity post, CommentEntity comment, ReportEntity first, Instant now) {
        Long authorId = c.getTargetAuthorId();
        if (authorId == null) {
            String guest = c.isComment() ? (comment == null ? null : comment.getGuestName()) : (post == null ? null : post.getGuestName());
            if (guest == null && first != null) {
                guest = first.getSnapshotAuthorName();
            }
            return new ReportAuthorCardView(null, guest == null ? "" : guest, null, null, new ReportClosedCasesView(0, 0), null);
        }
        ProfileEntity profile = profiles.findById(authorId).orElse(null);
        UserEntity user = users.findById(authorId).orElse(null);
        long[] closed = search.closedCountsOf(authorId, now.minus(AUTHOR_WINDOW));
        return new ReportAuthorCardView(Long.toString(authorId), profile == null ? "" : profile.getNickname(), profile == null ? null : profile.getGradeKey(),
                user == null ? null : user.getCreatedAt(), new ReportClosedCasesView(closed[0], closed[1]), null);
    }

    private List<ReportEventView> eventViews(List<ReportEventEntity> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = operatorNames(rows.stream().map(ReportEventEntity::getActorId).filter(Objects::nonNull).distinct().toList());
        List<ReportEventView> out = new ArrayList<>(rows.size());
        for (ReportEventEntity e : rows) {
            ReportEventActorView actor = new ReportEventActorView(e.getActorKind(), e.getActorId() == null ? null : Long.toString(e.getActorId()),
                    e.getActorId() == null ? null : names.get(e.getActorId()));
            out.add(new ReportEventView(Long.toString(e.getId()), e.getType(), actor, detailOf(e.getDetail()), e.getCreatedAt()));
        }
        return out;
    }

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
            log.warn("신고 사건 기록의 덧붙임을 읽지 못해 비운다 원인={}", e.getClass().getSimpleName());
            return null;
        }
    }

    /** 저장된 본문의 보이는 글자 — HTML 이면 태그를 떼고 엔티티를 푼다(목록 발췌용 — 공개 서버 RichText.plainText 의 간단한 판) */
    static String plain(ContentFormat format, String content) {
        if (content == null) {
            return null;
        }
        if (format != ContentFormat.HTML) {
            return content;
        }
        String s = content.replaceAll("<[^>]*>", " ");
        return HtmlUtils.htmlUnescape(s).replace(' ', ' ');
    }

    /** 앞 {@value #EXCERPT_MAX}자 — 공백은 한 칸 */
    static String excerpt(String text) {
        if (text == null) {
            return null;
        }
        String flat = text.strip().replaceAll("\\s+", " ");
        return flat.codePointCount(0, flat.length()) <= EXCERPT_MAX ? flat : flat.substring(0, flat.offsetByCodePoints(0, EXCERPT_MAX));
    }

    // ── 요청 값 풀기 ──────────────────────────────────────────────────────

    private AdminReportRepositoryQDSL.Search criteria(String status, String targetType, String reason, String searchField, String q, String fromRaw, String toRaw,
                                                     String board, String postIdRaw, String sort) {
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        String field = trim(searchField) == null ? FIELD_TITLE : trim(searchField).toUpperCase(Locale.ROOT);
        if (!FIELD_TITLE.equals(field) && !FIELD_AUTHOR.equals(field) && !FIELD_CASE_NO.equals(field)) {
            invalid.put("searchField", new MessageRef("report.admin.query.searchField.invalid"));
        }
        Instant from = instant("from", fromRaw, invalid);
        Instant to = instant("to", toRaw, invalid);

        boolean none = false;
        Long caseNo = null;
        String title = null;
        String author = null;
        String query = trim(q);
        if (query != null && FIELD_CASE_NO.equals(field)) {
            String digits = query.startsWith("#") ? query.substring(1).strip() : query;
            if (digits.isEmpty() || !digits.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
                invalid.put("q", new MessageRef("report.admin.query.caseNo.invalid"));
            } else {
                caseNo = parseId(digits);
                none = caseNo == null;
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
            throw ValidationException.of("to", new MessageRef("post.admin.query.period.reversed"));
        }

        String st = trim(status) == null ? STATUS_OPEN : trim(status).toUpperCase(Locale.ROOT);
        List<String> statuses = switch (st) {
            case STATUS_ALL -> null;
            case ReportCaseEntity.PENDING, ReportCaseEntity.IN_REVIEW, ReportCaseEntity.RESOLVED, ReportCaseEntity.DISMISSED -> List.of(st);
            default -> ReportCaseEntity.OPEN;
        };
        String type = trim(targetType) == null ? null : trim(targetType).toUpperCase(Locale.ROOT);
        if (!ReportEntity.POST.equals(type) && !ReportEntity.COMMENT.equals(type)) {
            type = null;
        }
        String reasonCode = trim(reason) == null ? null : trim(reason).toUpperCase(Locale.ROOT);
        if (reasonCode != null && !ReportReasons.ALL.contains(reasonCode)) {
            reasonCode = null;
        }
        Long boardId = null;
        if (trim(board) != null) {
            boardId = parseId(board);
            none = none || boardId == null;
        }
        Long postId = null;
        if (trim(postIdRaw) != null) {
            postId = parseId(postIdRaw);
            none = none || postId == null;
        }
        String order = trim(sort) == null ? AdminReportRepositoryQDSL.SORT_NEWEST : trim(sort).toLowerCase(Locale.ROOT);
        if (!AdminReportRepositoryQDSL.SORT_OLDEST.equals(order) && !AdminReportRepositoryQDSL.SORT_MOST.equals(order)) {
            order = AdminReportRepositoryQDSL.SORT_NEWEST;
        }
        return new AdminReportRepositoryQDSL.Search(statuses, type, reasonCode, title, author, caseNo, from, to, boardId, postId, order, none);
    }

    /** UTC ISO 순간(게시글 관리 {@code createdFrom}·{@code createdTo} 와 같은 규칙) — 비었으면 null, 형식이 틀리면 칸 오류 */
    private static Instant instant(String field, String raw, Map<String, MessageRef> invalid) {
        String s = trim(raw);
        if (s == null) {
            return null;
        }
        try {
            Instant at = Instant.parse(s);
            if (at.isBefore(INSTANT_MIN) || at.isAfter(INSTANT_MAX)) {
                throw new DateTimeParseException("out of range", s, 0);
            }
            return at;
        } catch (DateTimeParseException e) {
            invalid.put(field, new MessageRef("post.admin.query.date.invalid"));
            return null;
        }
    }

    static String trim(String raw) {
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
