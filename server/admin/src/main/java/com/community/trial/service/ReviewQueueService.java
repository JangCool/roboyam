package com.community.trial.service;

import com.community.board.service.BoardCatalog;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.permission.exception.PermissionDeniedException;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostRepository;
import com.community.title.entity.TitleApplicationEntity;
import com.community.title.entity.TitleApplicationStatus;
import com.community.title.entity.TitleEntity;
import com.community.title.repository.master.TitleQueries;
import com.community.title.repository.master.TitleRepository;
import com.community.trial.dto.TrialViews;
import com.community.trial.entity.TrialDamageReportEntity;
import com.community.trial.entity.TrialDamageStatus;
import com.community.trial.entity.TrialEntity;
import com.community.trial.entity.TrialParticipantEntity;
import com.community.trial.entity.TrialSponsorReviewEntity;
import com.community.trial.entity.QTrialDamageReportEntity;
import com.community.trial.repository.master.TrialParticipantRepository;
import com.community.trial.repository.master.TrialQueries;
import com.community.trial.repository.master.TrialRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 「확인할 일」 목록 하나(trial-group-contract 「구현 결정」 7 — 칭호 계약과 같은 목록). 종류 {@code TITLE_APPLICATION}(칭호 신청 — {@code title.review} 또는 {@code title.read}) ·
 * {@code PURCHASE_PROOF} · {@code RETURN_CHECK} · {@code DAMAGE_REPORT} · {@code SPONSOR_HIDDEN}({@code trial.review}). 상태 PENDING(기본 — 처리할 것) · DONE(처리됨) · ALL.
 * 종류를 비우면 볼 수 있는 종류를 모두 섞어 최근 차례(대기는 오래된 차례)로 준다. 볼 수 없는 종류를 고르면 403. 처리는 각 기능의 API(칭호 신청 승인·거절, 구매 인증 승인·거절,
 * 반납 받음, 파손 판정, 협찬 숨김 확인·무시)로 한다. 종류마다 최근 200건까지.
 */
@Service
public class ReviewQueueService {

    public static final List<String> TYPES = List.of("TITLE_APPLICATION", "PURCHASE_PROOF", "RETURN_CHECK", "DAMAGE_REPORT", "SPONSOR_HIDDEN");
    static final int LIMIT = 200;
    private static final QTrialDamageReportEntity D = QTrialDamageReportEntity.trialDamageReportEntity;

    /** 목록 한 줄 — {@code refs} 는 화면이 처리 화면으로 갈 때 쓰는 ID(글자) */
    public record Item(String type, String id, String status, String title, TrialViews.Member member, Instant at, String note, Map<String, String> refs) {
    }

    /** 응답 — 줄 · 종류마다 대기 수(볼 수 있는 종류만) */
    public record Page(List<Item> items, Map<String, Integer> pending) {
    }

    private final TitleQueries titleQueries;
    private final TitleRepository titles;
    private final PurchaseProofService proofs;
    private final SponsorReviewService sponsors;
    private final TrialQueries trialQueries;
    private final TrialRepository trials;
    private final TrialParticipantRepository participants;
    private final TrialViewer viewer;
    private final PostRepository posts;
    private final BoardCatalog catalog;
    private final JPAQueryFactory query;
    private final Clock clock;

    public ReviewQueueService(TitleQueries titleQueries, TitleRepository titles, PurchaseProofService proofs, SponsorReviewService sponsors, TrialQueries trialQueries,
                              TrialRepository trials, TrialParticipantRepository participants, TrialViewer viewer, PostRepository posts, BoardCatalog catalog,
                              JPAQueryFactory query, ObjectProvider<Clock> clocks) {
        this.titleQueries = titleQueries;
        this.titles = titles;
        this.proofs = proofs;
        this.sponsors = sponsors;
        this.trialQueries = trialQueries;
        this.trials = trials;
        this.participants = participants;
        this.viewer = viewer;
        this.posts = posts;
        this.catalog = catalog;
        this.query = query;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Transactional(readOnly = true)
    public Page list(AdminActor actor, String typeRaw, String statusRaw) {
        String status = statusRaw == null || statusRaw.isBlank() ? "PENDING" : statusRaw.strip().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("PENDING", "DONE", "ALL").contains(status)) {
            throw ValidationException.of("status", new MessageRef("trial.value.invalid"));
        }
        List<String> allowed = TYPES.stream().filter(t -> can(actor, t)).toList();
        List<String> types;
        if (typeRaw == null || typeRaw.isBlank()) {
            types = allowed;
        } else {
            String t = typeRaw.strip().toUpperCase(java.util.Locale.ROOT);
            if (!TYPES.contains(t)) {
                throw ValidationException.of("type", new MessageRef("trial.value.invalid"));
            }
            if (!allowed.contains(t)) {
                throw new PermissionDeniedException();
            }
            types = List.of(t);
        }
        Instant now = clock.instant();
        List<Item> items = new ArrayList<>();
        for (String t : types) {
            if (!"DONE".equals(status)) {
                items.addAll(items(t, true, now));
            }
            if (!"PENDING".equals(status)) {
                items.addAll(items(t, false, now));
            }
        }
        Comparator<Item> order = Comparator.comparing(Item::at, Comparator.nullsLast(Comparator.naturalOrder()));
        items.sort("PENDING".equals(status) ? order : order.reversed());
        Map<String, Integer> pending = new LinkedHashMap<>();
        for (String t : allowed) {
            pending.put(t, items(t, true, now).size());
        }
        return new Page(items, pending);
    }

    private static boolean can(AdminActor actor, String type) {
        return "TITLE_APPLICATION".equals(type) ? actor.can(Permission.TITLE_REVIEW) || actor.can(Permission.TITLE_READ) : actor.can(Permission.TRIAL_REVIEW);
    }

    private List<Item> items(String type, boolean pending, Instant now) {
        return switch (type) {
            case "TITLE_APPLICATION" -> titleApplications(pending);
            case "PURCHASE_PROOF" -> proofs.list(pending ? "PENDING" : "DONE", null).stream().map(p -> new Item(type, p.id(), p.status(), p.postTitle(), p.author(),
                    pending ? p.createdAt() : p.reviewedAt(), p.rejectReason(), refs("postId", p.postId(), "boardSlug", p.board() == null ? null : p.board().slug()))).toList();
            case "RETURN_CHECK" -> returnChecks(pending, now);
            case "DAMAGE_REPORT" -> damages(pending);
            case "SPONSOR_HIDDEN" -> sponsorHidden(pending);
            default -> List.of();
        };
    }

    private List<Item> titleApplications(boolean pending) {
        List<TitleApplicationEntity> rows = pending ? titleQueries.applications(TitleApplicationStatus.PENDING, null, LIMIT)
                : titleQueries.applications(null, null, LIMIT).stream().filter(a -> !a.isPending()).toList();
        Map<Long, TitleEntity> byId = new HashMap<>();
        titles.findAllById(rows.stream().map(TitleApplicationEntity::getTitleId).distinct().toList()).forEach(t -> byId.put(t.getId(), t));
        Map<Long, TrialViews.Member> who = viewer.members(rows.stream().map(TitleApplicationEntity::getUserId).toList());
        return rows.stream().map(a -> {
            TitleEntity t = byId.get(a.getTitleId());
            return new Item("TITLE_APPLICATION", Long.toString(a.getId()), a.getStatus().name(), t == null ? null : t.getName(), who.get(a.getUserId()),
                    pending ? a.getCreatedAt() : a.getReviewedAt(), a.getReason(), refs("titleKey", t == null ? null : t.getTitleKey()));
        }).toList();
    }

    private List<Item> returnChecks(boolean pending, Instant now) {
        List<TrialParticipantEntity> rows = trialQueries.returnChecks(pending, now, LIMIT);
        Map<Long, TrialEntity> byTrial = trialsOf(rows.stream().map(TrialParticipantEntity::getTrialId).toList());
        Map<Long, TrialViews.Member> who = viewer.members(rows.stream().map(TrialParticipantEntity::getUserId).toList());
        return rows.stream().map(p -> new Item("RETURN_CHECK", Long.toString(p.getId()), pending ? "PENDING" : p.returned() ? "RETURNED" : "UNRETURNED",
                byTrial.containsKey(p.getTrialId()) ? byTrial.get(p.getTrialId()).getTitle() : null, who.get(p.getUserId()),
                pending ? p.getReturnDueAt() : (p.returned() ? p.getReturnedAt() : p.getUnreturnedAt()), null, refs("trialId", Long.toString(p.getTrialId())))).toList();
    }

    private List<Item> damages(boolean pending) {
        List<TrialDamageReportEntity> rows = pending
                ? query.selectFrom(D).where(D.status.in(TrialDamageStatus.OBJECTED, TrialDamageStatus.AWAITING)).orderBy(D.createdAt.asc()).limit(LIMIT).fetch()
                : query.selectFrom(D).where(D.status.eq(TrialDamageStatus.DECIDED)).orderBy(D.decidedAt.desc()).limit(LIMIT).fetch();
        Map<Long, TrialEntity> byTrial = trialsOf(rows.stream().map(TrialDamageReportEntity::getTrialId).toList());
        Map<Long, Long> owner = new HashMap<>();
        participants.findAllById(rows.stream().map(TrialDamageReportEntity::getParticipantId).distinct().toList()).forEach(p -> owner.put(p.getId(), p.getUserId()));
        Map<Long, TrialViews.Member> who = viewer.members(new HashSet<>(owner.values()));
        return rows.stream().map(d -> new Item("DAMAGE_REPORT", Long.toString(d.getId()), d.getStatus() == TrialDamageStatus.DECIDED ? d.getVerdict().name() : d.getStatus().name(),
                byTrial.containsKey(d.getTrialId()) ? byTrial.get(d.getTrialId()).getTitle() : null,
                owner.containsKey(d.getParticipantId()) ? who.get(owner.get(d.getParticipantId())) : null, pending ? d.getCreatedAt() : d.getDecidedAt(),
                d.getObjection() != null ? d.getObjection() : d.getDescription(),
                refs("trialId", Long.toString(d.getTrialId()), "participantId", Long.toString(d.getParticipantId())))).toList();
    }

    private List<Item> sponsorHidden(boolean pending) {
        List<TrialSponsorReviewEntity> rows = sponsors.list(pending, LIMIT);
        Map<Long, PostEntity> byPost = new HashMap<>();
        posts.findAllById(rows.stream().map(TrialSponsorReviewEntity::getPostId).toList()).forEach(p -> byPost.put(p.getId(), p));
        Set<Long> authors = new HashSet<>();
        byPost.values().forEach(p -> {
            if (p.getAuthorId() != null && !p.isAnonymous()) {
                authors.add(p.getAuthorId());
            }
        });
        Map<Long, TrialViews.Member> who = viewer.members(authors);
        return rows.stream().map(r -> {
            PostEntity p = byPost.get(r.getPostId());
            String slug = p == null ? null : catalog.snapshot().board(p.getBoardId()).map(b -> b.slug()).orElse(null);
            return new Item("SPONSOR_HIDDEN", Long.toString(r.getPostId()), r.getStatus().name(), p == null ? null : p.getTitle(),
                    p == null || p.getAuthorId() == null || p.isAnonymous() ? null : who.get(p.getAuthorId()), pending ? r.getLastReportedAt() : r.getDecidedAt(), r.getNote(),
                    refs("postId", Long.toString(r.getPostId()), "boardSlug", slug, "reportCount", String.valueOf(r.getReportCount())));
        }).toList();
    }

    private Map<Long, TrialEntity> trialsOf(List<Long> ids) {
        Map<Long, TrialEntity> out = new HashMap<>();
        trials.findAllById(ids.stream().distinct().toList()).forEach(t -> out.put(t.getId(), t));
        return out;
    }

    private static Map<String, String> refs(String... pairs) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                out.put(pairs[i], pairs[i + 1]);
            }
        }
        return out;
    }
}
