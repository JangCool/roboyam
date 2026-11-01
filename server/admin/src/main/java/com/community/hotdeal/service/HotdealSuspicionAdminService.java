package com.community.hotdeal.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.hotdeal.dto.request.HotdealAdminRequest;
import com.community.hotdeal.dto.response.HotdealAdminView;
import com.community.hotdeal.dto.response.HotdealSuspicionView;
import com.community.hotdeal.entity.DealViews;
import com.community.hotdeal.exception.HotdealStateChangedException;
import com.community.hotdeal.exception.HotdealSuspicionNotFoundException;
import com.community.hotdeal.repository.master.AdminDealQueries;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.post.repository.master.AdminPostRepositoryQDSL;
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

/**
 * 관리자 위장 홍보 의심 후보({@code deal_suspicions}) — 조회 {@code hotdeal.read}, 무시 {@code hotdeal.manage}. 후보는 배치 {@code hotdeal-suspicion}({@link DealSuspicionService})이 연다.
 * <b>자동 제재 없음</b> — 제재는 글 관리·회원·신고 화면.
 *
 * <ul>
 *   <li><b>목록</b>: {@code status} OPEN(기본)·DISMISSED, {@code kind} MEMBER·STORE(빠지면 둘 다), 마지막 글이 최근인 것부터, {@code size} 기본 20 · 최대 50.
 *       {@code counts} = 상태별(종류 조건 포함), {@code kindCounts} = 종류별(상태 조건 포함 — ALL·MEMBER·STORE).</li>
 *   <li><b>줄</b>: {@code lastPostAt} = 후보 글 가운데 가장 최근 등록, 회원 후보 {@code othersCount} = 같은 판매처에 기간 안 글을 올린 다른 회원 수(읽을 때 센다 — 사업자 글 제외).</li>
 *   <li><b>상세</b>: 글 — 회원 후보 최근 10개, 판매처 후보 최근 30개(지운 글도 셈에 들었으면 보인다). 판매처 후보 {@code members[]} = 회원별 기간 안 이 판매처 글 수 · 다른 판매처 핫딜 수.</li>
 *   <li><b>무시</b>: 메모 0~200자({@code 400 fields.note}) → 없음 {@code 404 HOTDEAL_SUSPICION_NOT_FOUND} → 이미 무시함 {@code 409 HOTDEAL_STATE_CHANGED}(조건부 UPDATE — 동시 무시도 한 번).
 *       {@code status DISMISSED} 와 {@code dismissed_at} 을 같이(CHECK). 감사 {@code HOTDEAL_SUSPICION_DISMISS}(대상 HOTDEAL_SUSPICION). 무시한 대상은 무시 뒤 새 글이 생겨야 다시 열린다.</li>
 * </ul>
 */
@Service
public class HotdealSuspicionAdminService {

    private static final Logger log = LoggerFactory.getLogger(HotdealSuspicionAdminService.class);

    static final String TARGET = "HOTDEAL_SUSPICION";
    static final String OPEN = "OPEN";
    static final String DISMISSED = "DISMISSED";
    static final int MEMBER_POSTS = 10;
    static final int STORE_POSTS = 30;
    public static final int NOTE_MAX = 200;

    private final AdminDealQueries queries;
    private final DealSettingsReader settings;
    private final BoardCatalog catalog;
    private final AdminPostRepositoryQDSL posts;
    private final ProfileRepository profiles;
    private final UserRepository users;
    private final OperatorRepository operators;
    private final AuditLogService audit;

    public HotdealSuspicionAdminService(AdminDealQueries queries, DealSettingsReader settings, BoardCatalog catalog, AdminPostRepositoryQDSL posts,
                                        ProfileRepository profiles, UserRepository users, OperatorRepository operators, AuditLogService audit) {
        this.queries = queries;
        this.settings = settings;
        this.catalog = catalog;
        this.posts = posts;
        this.profiles = profiles;
        this.users = users;
        this.operators = operators;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public HotdealSuspicionView.Page list(String statusRaw, String kindRaw, String pageRaw, String sizeRaw) {
        String status = DISMISSED.equalsIgnoreCase(HotdealAdminQueryService.trim(statusRaw)) ? DISMISSED : OPEN;
        String k = HotdealAdminQueryService.trim(kindRaw) == null ? null : HotdealAdminQueryService.trim(kindRaw).toUpperCase(Locale.ROOT);
        String kind = DealSuspicionService.MEMBER.equals(k) || DealSuspicionService.STORE.equals(k) ? k : null;
        int page = Math.max(0, HotdealAdminQueryService.intOr(pageRaw, 0));
        int size = Math.min(HotdealAdminQueryService.SIZE_MAX, Math.max(1, HotdealAdminQueryService.intOr(sizeRaw, HotdealAdminQueryService.SIZE_DEFAULT)));

        Map<String, Map<String, Long>> all = queries.suspicionCounts();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String st : List.of(OPEN, DISMISSED)) {
            Map<String, Long> byKind = all.getOrDefault(st, Map.of());
            counts.put(st, kind == null ? byKind.values().stream().mapToLong(Long::longValue).sum() : byKind.getOrDefault(kind, 0L));
        }
        Map<String, Long> byKind = all.getOrDefault(status, Map.of());
        Map<String, Long> kindCounts = new LinkedHashMap<>();
        kindCounts.put("ALL", byKind.values().stream().mapToLong(Long::longValue).sum());
        kindCounts.put(DealSuspicionService.MEMBER, byKind.getOrDefault(DealSuspicionService.MEMBER, 0L));
        kindCounts.put(DealSuspicionService.STORE, byKind.getOrDefault(DealSuspicionService.STORE, 0L));
        long total = kind == null ? kindCounts.get("ALL") : kindCounts.get(kind);

        List<AdminDealQueries.SuspicionRow> rows = queries.suspicions(status, kind, (long) page * size, size);
        return new HotdealSuspicionView.Page(total, page, size, views(rows), counts, kindCounts);
    }

    @Transactional(readOnly = true)
    public HotdealSuspicionView.Detail detail(String idText) {
        AdminDealQueries.SuspicionRow s = find(idText);
        HotdealSuspicionView.Suspicion suspicion = views(List.of(s)).get(0);
        boolean store = DealSuspicionService.STORE.equals(s.kind());
        List<AdminDealQueries.SuspicionPost> rows = queries.suspicionPosts(s.postIds(), store ? STORE_POSTS : MEMBER_POSTS);
        Set<Long> authorIds = new LinkedHashSet<>();
        rows.stream().map(AdminDealQueries.SuspicionPost::authorId).filter(Objects::nonNull).forEach(authorIds::add);
        Map<Long, long[]> perMember = new LinkedHashMap<>();
        if (store) {
            Set<Long> everyone = new LinkedHashSet<>(authorIds);
            queries.suspicionPosts(s.postIds(), Integer.MAX_VALUE).stream().map(AdminDealQueries.SuspicionPost::authorId).filter(Objects::nonNull).forEach(everyone::add);
            perMember = queries.storeMembers(s.host(), everyone, s.windowDays(), HotdealAdminQueryService.now());
            authorIds.addAll(perMember.keySet());
        }
        Members members = members(authorIds);
        Map<Long, Integer> reports = posts.openReports(rows.stream().map(AdminDealQueries.SuspicionPost::postId).toList());
        BoardCatalogSnapshot snap = catalog.snapshot();
        int days = settings.current().autoExpireDays();
        Instant now = Instant.now();
        List<HotdealSuspicionView.Post> postViews = new ArrayList<>(rows.size());
        for (AdminDealQueries.SuspicionPost p : rows) {
            EffectiveBoard b = p.boardId() == null ? null : snap.board(p.boardId()).orElse(null);
            HotdealAdminView.BoardRef board = new HotdealAdminView.BoardRef(p.boardId() == null ? null : Long.toString(p.boardId()), b == null ? null : b.slug(),
                    b == null ? null : b.name(), b == null ? null : b.nameEn());
            postViews.add(new HotdealSuspicionView.Post(Long.toString(p.postId()), p.title(), board, members.of(p.authorId()), p.dealPrice(),
                    DealViews.view(p.status(), p.flaggedAt(), p.endsAt(), p.createdAt(), days, now).name(), p.createdAt(), reports.getOrDefault(p.postId(), 0)));
        }
        List<HotdealSuspicionView.StoreMember> memberViews = new ArrayList<>();
        for (Map.Entry<Long, long[]> e : perMember.entrySet()) {
            memberViews.add(new HotdealSuspicionView.StoreMember(members.of(e.getKey()), e.getValue()[0], e.getValue()[1]));
        }
        return new HotdealSuspicionView.Detail(suspicion, postViews, memberViews);
    }

    @Transactional
    public void dismiss(AdminActor actor, String idText, HotdealAdminRequest.Dismiss body) {
        String note = body == null || body.note() == null ? null : body.note().replace("\u0000", "").strip();
        if (note != null && note.isEmpty()) {
            note = null;
        }
        if (note != null && note.codePointCount(0, note.length()) > NOTE_MAX) {
            throw ValidationException.of("note", new MessageRef("hotdeal.admin.note.tooLong", String.valueOf(NOTE_MAX)));
        }
        AdminDealQueries.SuspicionRow s = find(idText);
        if (queries.dismissSuspicion(s.id(), actor.operatorId(), note, Instant.now().truncatedTo(ChronoUnit.MICROS)) == 0) {
            throw new HotdealStateChangedException();
        }
        AuditEvent event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.HOTDEAL_SUSPICION_DISMISS, AuditResult.SUCCESS)
                .target(TARGET, Long.toString(s.id())).with("kind", s.kind()).with("host", s.host());
        if (s.memberId() != null) {
            event = event.with("memberId", Long.toString(s.memberId()));
        }
        if (note != null) {
            event = event.with("note", note);
        }
        audit.record(event);
        log.info("의심 후보 무시 id={} kind={} by operatorId={}", s.id(), s.kind(), actor.operatorId());
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    private AdminDealQueries.SuspicionRow find(String idText) {
        Long id = HotdealAdminQueryService.parseId(idText);
        AdminDealQueries.SuspicionRow s = id == null ? null : queries.suspicion(id);
        if (s == null) {
            throw new HotdealSuspicionNotFoundException();
        }
        return s;
    }

    private List<HotdealSuspicionView.Suspicion> views(List<AdminDealQueries.SuspicionRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Members members = members(rows.stream().map(AdminDealQueries.SuspicionRow::memberId).filter(Objects::nonNull).toList());
        Map<Long, String> names = new HashMap<>();
        List<Long> dismissers = rows.stream().map(AdminDealQueries.SuspicionRow::dismissedBy).filter(Objects::nonNull).distinct().toList();
        if (!dismissers.isEmpty()) {
            operators.findAllById(dismissers).forEach(o -> names.put(o.getId(), o.getOperatorName()));
        }
        Instant now = HotdealAdminQueryService.now();
        List<HotdealSuspicionView.Suspicion> out = new ArrayList<>(rows.size());
        for (AdminDealQueries.SuspicionRow r : rows) {
            boolean member = DealSuspicionService.MEMBER.equals(r.kind());
            out.add(new HotdealSuspicionView.Suspicion(Long.toString(r.id()), r.kind(), r.host(), r.storeName() == null ? r.host() : r.storeName(), r.postCount(),
                    r.windowDays(), r.detectedAt(), r.lastPostAt() == null ? r.detectedAt() : r.lastPostAt(), r.status(),
                    r.dismissedBy() == null ? null : names.get(r.dismissedBy()), r.dismissedAt(), r.note(),
                    member ? members.of(r.memberId()) : null,
                    member && r.memberId() != null ? queries.otherMembers(r.host(), r.memberId(), r.windowDays(), now) : null,
                    member ? null : r.memberCount(),
                    member ? null : (r.otherRatioPercent() == null ? 0 : r.otherRatioPercent())));
        }
        return out;
    }

    /** 회원 이름표(탈퇴·프로필 없음은 withdrawn) */
    private record Members(Map<Long, ProfileEntity> profiles, Map<Long, UserEntity> users) {

        HotdealSuspicionView.Member of(Long id) {
            if (id == null) {
                return new HotdealSuspicionView.Member(null, "", false);
            }
            ProfileEntity p = profiles.get(id);
            UserEntity u = users.get(id);
            return new HotdealSuspicionView.Member(Long.toString(id), p == null ? "" : p.getNickname(), u == null || u.isGone() || p == null);
        }
    }

    private Members members(Collection<Long> ids) {
        Map<Long, ProfileEntity> profileById = new HashMap<>();
        Map<Long, UserEntity> userById = new HashMap<>();
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (!distinct.isEmpty()) {
            profiles.findAllById(distinct).forEach(p -> profileById.put(p.getUserId(), p));
            users.findAllById(distinct).forEach(u -> userById.put(u.getId(), u));
        }
        return new Members(profileById, userById);
    }
}
