package com.community.hotdeal.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.hotdeal.dto.DealSettings;
import com.community.hotdeal.dto.response.HotdealAdminView;
import com.community.hotdeal.dto.response.HotdealAdminView.Author;
import com.community.hotdeal.dto.response.HotdealAdminView.BoardRef;
import com.community.hotdeal.dto.response.HotdealAdminView.PrefixRef;
import com.community.hotdeal.dto.response.HotdealAdminView.Row;
import com.community.hotdeal.dto.response.PostDealView;
import com.community.hotdeal.entity.PostDealEntity;
import com.community.hotdeal.entity.PostDealRevisionEntity;
import com.community.hotdeal.repository.master.AdminDealQueries;
import com.community.hotdeal.repository.master.DealQueries;
import com.community.hotdeal.repository.master.PostDealRepository;
import com.community.hotdeal.repository.master.PostDealRevisionRepository;
import com.community.hotdeal.service.DealLinkJudge.DomainRule;
import com.community.operator.repository.master.OperatorRepository;
import com.community.post.exception.AdminPostNotFoundException;
import com.community.post.repository.master.AdminPostRepositoryQDSL;
import com.community.site.dto.SiteValues;
import com.community.site.service.SiteSettingsService;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 핫딜 글 읽기({@code hotdeal.read}) — 게시판 고르기 · 목록 · 상세 · 딜 칸 수정 이력. docs/features/hotdeal-contract.md 「API 후보」 관리자, admin-web {@code useHotdealApi}.
 *
 * <ul>
 *   <li><b>대상</b>: 지금 핫딜 게시판({@code kind = DEAL}, 꺼진 게시판 포함)에 있는 살아 있는 글(지움·예약 삭제 지남 제외, 숨김은 든다) 가운데 딜 줄이 있는 것.</li>
 *   <li><b>상태</b>: 우선순위 ENDED &gt; EXPIRED &gt; FLAGGED &gt; ACTIVE(읽을 때 지금 설정으로 계산 — 공개 쪽과 같은 shared {@link DealViewAssembler}). {@code view} 는 그중 하나 또는 ALL
 *       (모르는 값은 ALL). {@code counts} 는 상태 조건만 뺀 같은 조건의 수.</li>
 *   <li><b>차단 도메인 진행 중</b>({@code blockedActive=true}): 진행 중(ACTIVE·FLAGGED) 글 가운데 상품·최종 호스트가 지금 링크 규칙(사본 — 저장 직후엔 무효화)에서 차단인 것(서버 규칙 7 —
 *       글은 그대로 둔다). 줄의 {@code blockedHost} 는 같은 판정. {@code blockedActiveCount} 는 상태·차단 조건을 뺀 같은 조건의 수.</li>
 *   <li><b>검색</b>: {@code q} 앞뒤 공백을 뗀 2자 이상이면 제목·쇼핑몰 이름·작성자 닉네임 부분 일치(대소문자 무시, 50자에서 자른다). {@code board} = 게시판 ID(핫딜 게시판이 아니거나 깨진 ID 는
 *       아무 글도 안 맞음). 날짜는 UTC ISO 순간(양 끝 포함 — 형식 오류 400 {@code fields.createdFrom}·{@code createdTo}, 시작 &gt; 끝 400 {@code fields.createdTo}).
 *       {@code page} 0 부터, {@code size} 기본 20 · 최대 50.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class HotdealAdminQueryService {

    private static final Logger log = LoggerFactory.getLogger(HotdealAdminQueryService.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    static final int SIZE_DEFAULT = 20;
    static final int SIZE_MAX = 50;
    static final int QUERY_MIN = 2;
    static final int QUERY_MAX = 50;
    static final int SAME_DEALS_MAX = 5;
    static final List<String> VIEWS = List.of("ACTIVE", "FLAGGED", "ENDED", "EXPIRED", AdminDealQueries.ALL);
    /** 수정 이력에서 숫자로 내리는 칸 · 불로 내리는 칸(나머지는 글자) */
    private static final Set<String> NUMBER_FIELDS = Set.of("normalPrice", "dealPrice", "shippingFee");
    private static final Set<String> BOOLEAN_FIELDS = Set.of("affiliate", "dutyIncluded");

    private final AdminDealQueries queries;
    private final DealQueries dealQueries;
    private final PostDealRepository deals;
    private final PostDealRevisionRepository revisions;
    private final DealViewAssembler assembler;
    private final DealSettingsReader settings;
    private final DealRuleBook rules;
    private final BoardCatalog catalog;
    private final AdminPostRepositoryQDSL posts;
    private final ProfileRepository profiles;
    private final UserRepository users;
    private final OperatorRepository operators;
    private final SiteSettingsService site;

    public HotdealAdminQueryService(AdminDealQueries queries, DealQueries dealQueries, PostDealRepository deals, PostDealRevisionRepository revisions,
                                    DealViewAssembler assembler, DealSettingsReader settings, DealRuleBook rules, BoardCatalog catalog, AdminPostRepositoryQDSL posts,
                                    ProfileRepository profiles, UserRepository users, OperatorRepository operators, SiteSettingsService site) {
        this.queries = queries;
        this.dealQueries = dealQueries;
        this.deals = deals;
        this.revisions = revisions;
        this.assembler = assembler;
        this.settings = settings;
        this.rules = rules;
        this.catalog = catalog;
        this.posts = posts;
        this.profiles = profiles;
        this.users = users;
        this.operators = operators;
        this.site = site;
    }

    // ── 게시판 고르기 ──────────────────────────────────────────────────────

    /** 핫딜 게시판(꺼진 것 포함, 정렬 차례)과 켜진 말머리 */
    public HotdealAdminView.BoardOptions boards() {
        List<HotdealAdminView.BoardOption> out = dealBoards(catalog.snapshot()).stream()
                .map(b -> new HotdealAdminView.BoardOption(Long.toString(b.id()), b.slug(), b.name(), b.nameEn(), b.active(),
                        b.activePrefixes().stream().map(p -> new PrefixRef(Long.toString(p.id()), p.name())).toList()))
                .toList();
        return new HotdealAdminView.BoardOptions(out);
    }

    // ── 목록 ──────────────────────────────────────────────────────────────

    public HotdealAdminView.Page list(String viewRaw, String board, String q, String createdFrom, String createdTo, String blockedActiveRaw, String pageRaw,
                                      String sizeRaw) {
        Instant now = now();
        int page = Math.max(0, intOr(pageRaw, 0));
        int size = Math.min(SIZE_MAX, Math.max(1, intOr(sizeRaw, SIZE_DEFAULT)));
        String view = trim(viewRaw) == null ? AdminDealQueries.ALL : trim(viewRaw).toUpperCase(Locale.ROOT);
        if (!VIEWS.contains(view)) {
            view = AdminDealQueries.ALL;
        }
        boolean blockedOnly = "true".equalsIgnoreCase(trim(blockedActiveRaw));
        AdminDealQueries.Filter filter = filter(board, q, createdFrom, createdTo);
        DealSettings s = settings.current();
        Set<String> blocked = blockedHosts(filter.boardIds());

        AdminDealQueries.Page result = queries.search(filter, view, blockedOnly ? blocked : null, s.autoExpireDays(), now, (long) page * size, size);
        Map<String, Long> counts = queries.counts(filter, blockedOnly ? blocked : null, s.autoExpireDays(), now);
        long blockedCount = queries.blockedActiveCount(filter, blocked, s.autoExpireDays(), now);
        return new HotdealAdminView.Page(result.total(), page, size, rows(result.rows(), blocked, s, now), counts, blockedCount);
    }

    // ── 상세 ──────────────────────────────────────────────────────────────

    /** 상세 — 핫딜 글이 아니면 404 POST_NOT_FOUND */
    public HotdealAdminView.Detail detail(String idText) {
        Instant now = now();
        DealSettings s = settings.current();
        AdminDealQueries.Row r = requireRow(idText, now);
        Row row = rows(List.of(r), blockedHosts(List.of(r.boardId())), s, now).get(0);
        PostDealEntity d = deals.findById(r.postId()).orElseThrow(AdminPostNotFoundException::new);

        String endedByRole = d.endedByRole();
        String endedByName = null;
        if (endedByRole != null && d.getEndedById() != null) {
            // 운영자 → 운영자 이름, 작성자·게시판 매니저·스텝(회원) → 지금 닉네임
            endedByName = PostDealEntity.ROLE_OPERATOR.equals(endedByRole) ? operatorName(d.getEndedById()) : nickname(d.getEndedById());
        }
        List<AdminDealQueries.SameRow> same = queries.sameDeals(d.getProductUrlKey(), d.getPostId(), s.autoExpireDays(), now, SAME_DEALS_MAX);
        Map<Long, PostDealEntity> sameDeals = new HashMap<>();
        deals.findAllById(same.stream().map(AdminDealQueries.SameRow::postId).toList()).forEach(x -> sameDeals.put(x.getPostId(), x));
        List<HotdealAdminView.SameDeal> sameViews = same.stream().map(x -> {
            PostDealEntity other = sameDeals.get(x.postId());
            String v = other == null ? null : other.view(s.autoExpireDays(), now).name();
            return new HotdealAdminView.SameDeal(Long.toString(x.postId()), x.title(), x.dealPrice(), x.createdAt(), v);
        }).toList();
        return new HotdealAdminView.Detail(row, endedByName, endedByRole, d.isEnded() ? d.getEndNote() : null, d.getKeptAt(), publicUrlOf(d.getPostId()), sameViews,
                s.affiliateMode());
    }

    /** 딜 칸 수정 이력(최근 것부터) — 핫딜 글이 아니면 404 POST_NOT_FOUND */
    public HotdealAdminView.RevisionList revisions(String idText) {
        AdminDealQueries.Row r = requireRow(idText, now());
        List<PostDealRevisionEntity> rows = revisions.findByPostIdOrderByCreatedAtDescIdDesc(r.postId());
        List<HotdealAdminView.Revision> out = new ArrayList<>(rows.size());
        for (PostDealRevisionEntity e : rows) {
            String kind = e.getActorKind();
            String name = e.getActorId() == null ? null
                    : PostDealRevisionEntity.BY_AUTHOR.equals(kind) ? nickname(e.getActorId()) : actorName(e.getActorId());
            out.add(new HotdealAdminView.Revision(Long.toString(e.getId()), e.getCreatedAt(), new HotdealAdminView.RevisionActor(kind, name == null ? "" : name),
                    changes(e.getChanges()), e.isHostChanged()));
        }
        return new HotdealAdminView.RevisionList(out, PostDealRevisionEntity.RETENTION_MONTHS);
    }

    // ── 공용(종료·재개·신고 사건이 쓴다) ────────────────────────────────────

    /** 핫딜 게시판 글인가(딜 줄 있음 — 살아 있는 글만) */
    public boolean isDealPost(long postId) {
        return queries.row(postId, dealBoardIds(catalog.snapshot()), now()) != null;
    }

    /** 글 ID → 지금 딜 상태(핫딜 게시판에 있고 딜 줄이 있는 글만 — 지운 글도 상태는 낸다) */
    public Map<Long, String> dealViews(Collection<Long> postIds) {
        Map<Long, String> out = new HashMap<>();
        if (postIds.isEmpty()) {
            return out;
        }
        BoardCatalogSnapshot snap = catalog.snapshot();
        Set<Long> dealBoards = new LinkedHashSet<>(dealBoardIds(snap));
        DealSettings s = settings.current();
        Instant now = now();
        for (PostDealEntity d : deals.findAllById(postIds)) {
            out.put(d.getPostId(), d.view(s.autoExpireDays(), now).name());
        }
        // 지금 핫딜 게시판에 있는 글만(옮긴 글은 일반 글로 본다)
        Map<Long, Long> boardOf = queries.postBoards(out.keySet());
        out.keySet().removeIf(id -> !dealBoards.contains(boardOf.get(id)));
        return out;
    }

    // ── 줄 만들기 ──────────────────────────────────────────────────────────

    private List<Row> rows(List<AdminDealQueries.Row> rows, Set<String> blocked, DealSettings s, Instant now) {
        if (rows.isEmpty()) {
            return List.of();
        }
        BoardCatalogSnapshot snap = catalog.snapshot();
        List<Long> ids = rows.stream().map(AdminDealQueries.Row::postId).toList();
        Map<Long, PostDealEntity> dealById = new HashMap<>();
        deals.findAllById(ids).forEach(d -> dealById.put(d.getPostId(), d));
        Map<Long, DealQueries.EndCounts> counts = dealQueries.endRequestCounts(ids);
        Map<String, Integer> live = dealQueries.liveCountsByKey(dealById.values().stream().map(PostDealEntity::getProductUrlKey).distinct().toList(),
                s.autoExpireDays(), now);
        Map<Long, String> origins = queries.ogOrigins(dealById.values().stream().map(PostDealEntity::getOgImageId).filter(Objects::nonNull).distinct().toList());
        Map<Long, Integer> reports = posts.openReports(ids);
        List<Long> authorIds = rows.stream().map(AdminDealQueries.Row::authorId).filter(Objects::nonNull).distinct().toList();
        Map<Long, ProfileEntity> profileById = new HashMap<>();
        Map<Long, UserEntity> userById = new HashMap<>();
        if (!authorIds.isEmpty()) {
            profiles.findAllById(authorIds).forEach(p -> profileById.put(p.getUserId(), p));
            users.findAllById(authorIds).forEach(u -> userById.put(u.getId(), u));
        }
        List<Row> out = new ArrayList<>(rows.size());
        for (AdminDealQueries.Row r : rows) {
            PostDealEntity d = dealById.get(r.postId());
            if (d == null) {
                continue;
            }
            boolean live0 = d.view(s.autoExpireDays(), now).live();
            int same = Math.max(0, live.getOrDefault(d.getProductUrlKey(), 0) - (live0 && r.publicPost() ? 1 : 0));
            PostDealView deal = assembler.view(d, r.likeCount(), counts.get(d.getPostId()), null, same, s, now,
                    d.getOgImageId() == null ? null : origins.get(d.getOgImageId()));
            String blockedHost = !live0 ? null : blocked.contains(d.getProductHost()) ? d.getProductHost()
                    : d.getFinalHost() != null && blocked.contains(d.getFinalHost()) ? d.getFinalHost() : null;
            EffectiveBoard b = snap.board(r.boardId()).orElse(null);
            BoardRef board = new BoardRef(Long.toString(r.boardId()), b == null ? null : b.slug(), b == null ? null : b.name(), b == null ? null : b.nameEn());
            BoardPrefixInfo p = b == null ? null : b.prefix(r.prefixId()).orElse(null);
            out.add(new Row(Long.toString(r.postId()), r.title(), board, p == null ? null : new PrefixRef(Long.toString(p.id()), p.name()),
                    author(r.authorId(), profileById, userById), r.createdAt(), reports.getOrDefault(r.postId(), 0), blockedHost, deal, d.getClickCount()));
        }
        return out;
    }

    static Author author(Long authorId, Map<Long, ProfileEntity> profileById, Map<Long, UserEntity> userById) {
        if (authorId == null) {
            return new Author(null, "", false);
        }
        ProfileEntity profile = profileById.get(authorId);
        UserEntity user = userById.get(authorId);
        boolean withdrawn = user == null || user.isGone() || profile == null;
        return new Author(Long.toString(authorId), profile == null ? "" : profile.getNickname(), withdrawn);
    }

    /** 수정 이력 JSON → 칸들(값은 저장 모양 — 금액 정수 · 불 · 글자) */
    private static List<HotdealAdminView.RevisionChange> changes(String json) {
        List<HotdealAdminView.RevisionChange> out = new ArrayList<>();
        try {
            for (JsonNode c : MAPPER.readTree(json == null ? "[]" : json)) {
                String field = c.path("field").asString();
                out.add(new HotdealAdminView.RevisionChange(field, value(field, c.get("before")), value(field, c.get("after"))));
            }
        } catch (RuntimeException e) {
            log.warn("딜 칸 수정 이력을 읽지 못해 비운다 원인={}", e.getClass().getSimpleName());
        }
        return out;
    }

    private static Object value(String field, JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asString();
        if (NUMBER_FIELDS.contains(field)) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                return text;
            }
        }
        if (BOOLEAN_FIELDS.contains(field)) {
            return Boolean.parseBoolean(text);
        }
        return text;
    }

    /** 이 글이 지금 가는 차단 호스트 — 저장 상태 진행 중 딜의 호스트를 지금 규칙으로 하나씩 판정 */
    private Set<String> blockedHosts(Collection<Long> boardIds) {
        List<DomainRule> domains = rules.snapshot().domains();
        Set<String> out = new LinkedHashSet<>();
        for (String host : queries.activeHosts(boardIds)) {
            if (host != null && com.community.hotdeal.entity.DealDomainEntity.BLOCK.equals(DealLinkJudge.judgeHost(host, domains).rule())) {
                out.add(host);
            }
        }
        return out;
    }

    private AdminDealQueries.Row requireRow(String idText, Instant now) {
        Long id = parseId(idText);
        AdminDealQueries.Row r = id == null ? null : queries.row(id, dealBoardIds(catalog.snapshot()), now);
        if (r == null) {
            throw new AdminPostNotFoundException();
        }
        return r;
    }

    private AdminDealQueries.Filter filter(String board, String q, String createdFrom, String createdTo) {
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        Instant from = instant("createdFrom", createdFrom, invalid);
        Instant to = instant("createdTo", createdTo, invalid);
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw ValidationException.of("createdTo", new MessageRef("post.admin.query.period.reversed"));
        }
        List<Long> boardIds = dealBoardIds(catalog.snapshot());
        if (trim(board) != null) {
            Long id = parseId(board);
            boardIds = id != null && boardIds.contains(id) ? List.of(id) : List.of();
        }
        String text = trim(q);
        if (text != null && text.codePointCount(0, text.length()) < QUERY_MIN) {
            text = null;
        } else if (text != null && text.codePointCount(0, text.length()) > QUERY_MAX) {
            text = text.substring(0, text.offsetByCodePoints(0, QUERY_MAX));
        }
        return new AdminDealQueries.Filter(boardIds, text, from, to);
    }

    static List<EffectiveBoard> dealBoards(BoardCatalogSnapshot s) {
        return s.boards().stream().filter(b -> BoardFields.KIND_DEAL.equals(b.kind())).toList();
    }

    static List<Long> dealBoardIds(BoardCatalogSnapshot s) {
        return dealBoards(s).stream().map(EffectiveBoard::id).toList();
    }

    /** 운영자 이름 → 없으면 회원 닉네임(게시판 매니저·스텝 — 둘 다 같은 Snowflake 발급기라 겹치지 않는다) */
    String actorName(long id) {
        return operators.findById(id).map(o -> o.getOperatorName()).orElseGet(() -> nickname(id));
    }

    String nickname(long userId) {
        return profiles.findById(userId).map(ProfileEntity::getNickname).orElse(null);
    }

    String operatorName(Long id) {
        return id == null ? null : operators.findById(id).map(o -> o.getOperatorName()).orElse(null);
    }

    private String publicUrlOf(long postId) {
        return site.current().map(SiteValues::publicUrl).filter(url -> url != null && !url.isBlank())
                .map(url -> url.strip().replaceAll("/+$", "") + "/posts/" + postId).orElse(null);
    }

    // ── 요청 값 풀기 ──────────────────────────────────────────────────────

    private static final Instant INSTANT_MIN = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant INSTANT_MAX = Instant.parse("9999-12-31T23:59:59.999999999Z");

    private static Instant instant(String field, String raw, Map<String, MessageRef> invalid) {
        String s = trim(raw);
        if (s == null) {
            return null;
        }
        try {
            Instant at = Instant.parse(s);
            if (at.isBefore(INSTANT_MIN) || at.isAfter(INSTANT_MAX)) {
                throw new java.time.format.DateTimeParseException("out of range", s, 0);
            }
            return at;
        } catch (java.time.format.DateTimeParseException e) {
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

    static int intOr(String raw, int fallback) {
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

    /** 문자열 ID → long(형식이 틀리면 null) */
    static Long parseId(String raw) {
        if (raw == null || raw.isBlank() || raw.strip().length() > 19) {
            return null;
        }
        try {
            long id = Long.parseLong(raw.strip());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
