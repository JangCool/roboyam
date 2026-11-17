package com.community.title.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.permission.dto.AdminActor;
import com.community.pointshop.dto.AuthorLook;
import com.community.pointshop.service.ShopDecorationResolver;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostRepository;
import com.community.title.dto.request.AdminTitleCandidateRequest;
import com.community.title.dto.request.AdminTitleGrantRequest;
import com.community.title.dto.response.AdminMonthlyCandidatesView;
import com.community.title.dto.response.AdminTitleApplicationListView;
import com.community.title.dto.response.AdminTitleGrantPageView;
import com.community.title.dto.response.AdminTitleGrantView;
import com.community.title.entity.MemberTitleEntity;
import com.community.title.entity.TitleApplicationStatus;
import com.community.title.entity.TitleEntity;
import com.community.title.entity.TitleMonthlyCandidateEntity;
import com.community.title.exception.TitleException;
import com.community.title.repository.master.MemberTitleRepository;
import com.community.title.repository.master.TitleMonthlyCandidateRepository;
import com.community.title.repository.master.TitleQueries;
import com.community.title.repository.master.TitleRepository;
import com.community.title.service.TitleReviewService.Reviewer;
import com.community.user.entity.ProfileEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 칭호 수여·회수·신청 심사·이달의 작가 뽑기·주고 뺀 기록(member-title-contract 「구현 결정」 3·7·8).
 *
 * <ul>
 *   <li>수여: {@link TitleGrantService#award}(이달의 작가 인원·올해의 작가 자격·명예 작가 즉시 지급) + 감사 {@code TITLE_GRANT}(대상 MEMBER — 명예 작가가 따라오면 {@code honoraryGrantId}).</li>
 *   <li>회수: 사유 필수(400 {@code fields.reason}), 살아 있는 줄만, 감사 {@code TITLE_REVOKE}. 매니저가 준 칭호도 회수할 수 있다.</li>
 *   <li>신청 심사: {@link TitleReviewService}(운영자 행위자).</li>
 *   <li>이달의 작가 후보 = 그 달 살아 있는 우수 글 선정의 글쓴이 + 「후보에 넣기」 글의 글쓴이(AI 회원·운영팀 계정 빼고). 넣기·빼기 감사 {@code TITLE_CANDIDATE_ADD}·{@code TITLE_CANDIDATE_REMOVE}
 *       (대상 POST) — 이미 넣은 글을 다시 넣거나 넣지 않은 글을 빼면 아무 일도 하지 않는다(204).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AdminTitleAwardService {

    private final TitleRepository titles;
    private final MemberTitleRepository memberTitles;
    private final TitleMonthlyCandidateRepository candidates;
    private final TitleQueries queries;
    private final TitleGrantService grants;
    private final TitleReviewService reviews;
    private final TitleSettingsReader settings;
    private final AdminTitleViews views;
    private final PostRepository posts;
    private final ProfileRepository profiles;
    private final ShopDecorationResolver decorations;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;

    public AdminTitleAwardService(TitleRepository titles, MemberTitleRepository memberTitles, TitleMonthlyCandidateRepository candidates, TitleQueries queries,
                                  TitleGrantService grants, TitleReviewService reviews, TitleSettingsReader settings, AdminTitleViews views, PostRepository posts,
                                  ProfileRepository profiles, ShopDecorationResolver decorations, AuditLogService audit, SnowflakeIdGenerator ids) {
        this.titles = titles;
        this.memberTitles = memberTitles;
        this.candidates = candidates;
        this.queries = queries;
        this.grants = grants;
        this.reviews = reviews;
        this.settings = settings;
        this.views = views;
        this.posts = posts;
        this.profiles = profiles;
        this.decorations = decorations;
        this.audit = audit;
        this.ids = ids;
    }

    static Long id(String raw) {
        return raw == null || !raw.strip().matches("[0-9]{1,19}") ? null : Long.parseLong(raw.strip());
    }

    static String reason(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.strip();
        if (s.length() > 500) {
            throw new ValidationException(Map.of("reason", new MessageRef("title.reason.max")));
        }
        return s;
    }

    // ── 수여·회수 ────────────────────────────────────────────────────────

    @Transactional
    public AdminTitleGrantView grant(AdminActor actor, String key, AdminTitleGrantRequest body) {
        Long memberId = body == null ? null : id(body.memberId());
        if (memberId == null) {
            throw new ValidationException(Map.of("memberId", new MessageRef("title.member.required")));
        }
        String why = reason(body.reason());
        TitleGrantService.Granted g = grants.award(key, memberId, body.period(), body.tier(), actor.operatorId(), why);
        AuditEvent event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.TITLE_GRANT, AuditResult.SUCCESS).target("MEMBER", Long.toString(memberId))
                .with("titleKey", g.title().getTitleKey()).with("grantId", Long.toString(g.row().getId())).with("period", g.row().getPeriod())
                .with("tier", g.row().getTier() == null ? null : g.row().getTier().name());
        if (why != null) {
            event = event.with("reason", why);
        }
        if (g.honorary() != null) {
            event = event.with("honoraryGrantId", Long.toString(g.honorary().getId()));
        }
        audit.record(event);
        return views.grant(g.row(), g.honorary());
    }

    @Transactional
    public void revoke(AdminActor actor, String key, String grantIdText, String reasonRaw) {
        String why = reason(reasonRaw);
        if (why == null) {
            throw new ValidationException(Map.of("reason", new MessageRef("title.reason.required")));
        }
        Long grantId = id(grantIdText);
        if (grantId == null) {
            throw TitleException.grantNotFound();
        }
        MemberTitleEntity row = grants.revoke(key, grantId, actor.operatorId(), why);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.TITLE_REVOKE, AuditResult.SUCCESS).target("MEMBER", Long.toString(row.getUserId()))
                .with("titleKey", key).with("grantId", Long.toString(row.getId())).with("period", row.getPeriod()).with("reason", why));
    }

    public AdminTitleGrantPageView log(String key, String memberIdText, Integer page, Integer size) {
        Long titleId = null;
        if (key != null && !key.isBlank()) {
            titleId = titles.findByTitleKey(key.strip()).map(TitleEntity::getId).orElseThrow(TitleException::notFound);
        }
        Long memberId = memberIdText == null || memberIdText.isBlank() ? null : id(memberIdText);
        if (memberIdText != null && !memberIdText.isBlank() && memberId == null) {
            return new AdminTitleGrantPageView(List.of(), 0, 20, 0);
        }
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, 100);
        return new AdminTitleGrantPageView(views.grants(queries.grantLog(titleId, memberId, p, s)), p, s, queries.grantLogCount(titleId, memberId));
    }

    /** 회원 상세의 칭호 — 받은 줄 전부(회수 포함, 최신 순) */
    public List<AdminTitleGrantView> memberTitles(String memberIdText) {
        Long memberId = id(memberIdText);
        if (memberId == null || profiles.findById(memberId).isEmpty()) {
            throw TitleException.memberNotFound();
        }
        return views.grants(queries.allOf(memberId));
    }

    // ── 신청 심사 ────────────────────────────────────────────────────────

    public AdminTitleApplicationListView applications(String statusRaw, String key) {
        TitleApplicationStatus status = statusRaw == null || statusRaw.isBlank() || "ALL".equalsIgnoreCase(statusRaw) ? null
                : TitleApplicationStatus.parse(statusRaw).orElse(TitleApplicationStatus.PENDING);
        List<Long> titleIds = null;
        if (key != null && !key.isBlank()) {
            titleIds = List.of(titles.findByTitleKey(key.strip()).map(TitleEntity::getId).orElseThrow(TitleException::notFound));
        }
        return new AdminTitleApplicationListView(views.applications(reviews.list(status, titleIds)));
    }

    @Transactional
    public void approve(AdminActor actor, String idText, String note) {
        Long id = id(idText);
        if (id == null) {
            throw TitleException.applicationNotFound();
        }
        reviews.approve(id, Reviewer.operator(actor.operatorId(), actor.ip()), note);
    }

    @Transactional
    public void reject(AdminActor actor, String idText, String why) {
        Long id = id(idText);
        if (id == null) {
            throw TitleException.applicationNotFound();
        }
        reviews.reject(id, Reviewer.operator(actor.operatorId(), actor.ip()), why);
    }

    // ── 이달의 작가 뽑기 ───────────────────────────────────────────────────

    static String month(String raw, Instant now) {
        if (raw == null || raw.isBlank()) {
            return YearMonth.from(now.atZone(ServiceTime.ZONE)).toString();
        }
        String m = raw.strip();
        if (!m.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
            throw new ValidationException(Map.of("month", new MessageRef("title.period.month")));
        }
        return m;
    }

    public AdminMonthlyCandidatesView monthlyCandidates(String monthRaw) {
        String month = month(monthRaw, grants.now());
        YearMonth ym = YearMonth.parse(month);
        Instant from = ym.atDay(1).atStartOfDay(ServiceTime.ZONE).toInstant();
        Instant to = ym.plusMonths(1).atDay(1).atStartOfDay(ServiceTime.ZONE).toInstant();
        TitleEntity monthly = titles.findByTitleKey(BuiltinTitles.MONTHLY_WRITER).orElseThrow(TitleException::notFound);
        List<MemberTitleEntity> awardedRows = memberTitles.findByTitleIdAndPeriodAndRevokedAtIsNullOrderByGrantedAtAsc(monthly.getId(), month);
        Set<Long> awarded = new HashSet<>();
        awardedRows.forEach(r -> awarded.add(r.getUserId()));

        Map<Long, Long> featured = queries.featuredPosts(from, to); // 글 → 글쓴이
        Map<Long, Long> manual = new LinkedHashMap<>();
        candidates.findByMonthOrderByAddedAtAsc(month).forEach(c -> manual.put(c.getPostId(), c.getUserId()));
        Map<Long, Set<Long>> postsByMember = new LinkedHashMap<>();
        featured.forEach((post, user) -> postsByMember.computeIfAbsent(user, k -> new LinkedHashSet<>()).add(post));
        manual.forEach((post, user) -> postsByMember.computeIfAbsent(user, k -> new LinkedHashSet<>()).add(post));
        Set<Long> excluded = queries.excludedAmong(postsByMember.keySet());
        excluded.forEach(postsByMember::remove);

        Set<Long> allPosts = new HashSet<>();
        postsByMember.values().forEach(allPosts::addAll);
        Map<Long, String> titleOf = queries.postTitles(allPosts);
        List<Long> members = new ArrayList<>(postsByMember.keySet());
        Map<Long, AdminTitleGrantView.MemberView> memberViews = views.members(members);
        Map<Long, AuthorLook> looks = members.isEmpty() ? Map.of() : decorations.resolve(members);

        List<AdminMonthlyCandidatesView.Candidate> out = new ArrayList<>();
        for (Long user : members) {
            AuthorLook look = looks.get(user);
            List<AdminMonthlyCandidatesView.Post> list = postsByMember.get(user).stream()
                    .map(post -> new AdminMonthlyCandidatesView.Post(Long.toString(post), titleOf.getOrDefault(post, ""), featured.containsKey(post), manual.containsKey(post)))
                    .toList();
            out.add(new AdminMonthlyCandidatesView.Candidate(memberViews.get(user), look == null ? null : look.avatarUrl(), awarded.contains(user), list));
        }
        out.sort(Comparator.comparingInt((AdminMonthlyCandidatesView.Candidate c) -> -c.posts().size()).thenComparing(c -> c.member().id()));
        return new AdminMonthlyCandidatesView(month, settings.current().monthlyMax(), views.grants(awardedRows), out);
    }

    @Transactional
    public void addCandidate(AdminActor actor, AdminTitleCandidateRequest body) {
        Instant now = grants.now();
        String month = month(body == null ? null : body.month(), now);
        PostEntity post = post(body);
        if (post.getAuthorId() == null || queries.excluded(post.getAuthorId())) {
            throw TitleException.candidateInvalid();
        }
        if (candidates.findByMonthAndPostId(month, post.getId()).isPresent()) {
            return;
        }
        try {
            candidates.persist(TitleMonthlyCandidateEntity.add(ids.nextId(), month, post.getId(), post.getAuthorId(), actor.operatorId(), now));
            candidates.flush();
        } catch (DuplicateKeyException e) {
            return; // 동시에 넣었다 — 이미 있다
        }
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.TITLE_CANDIDATE_ADD, AuditResult.SUCCESS).target("POST", Long.toString(post.getId()))
                .with("month", month).with("memberId", Long.toString(post.getAuthorId())));
    }

    @Transactional
    public void removeCandidate(AdminActor actor, AdminTitleCandidateRequest body) {
        String month = month(body == null ? null : body.month(), grants.now());
        Long postId = body == null ? null : id(body.postId());
        if (postId == null) {
            throw TitleException.postNotFound();
        }
        candidates.findByMonthAndPostId(month, postId).ifPresent(c -> {
            candidates.delete(c);
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.TITLE_CANDIDATE_REMOVE, AuditResult.SUCCESS).target("POST", Long.toString(postId))
                    .with("month", month));
        });
    }

    private PostEntity post(AdminTitleCandidateRequest body) {
        Long postId = body == null ? null : id(body.postId());
        return (postId == null ? java.util.Optional.<PostEntity>empty() : posts.findById(postId)).filter(p -> p.getDeletedAt() == null)
                .orElseThrow(TitleException::postNotFound);
    }
}
