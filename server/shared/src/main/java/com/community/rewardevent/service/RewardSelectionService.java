package com.community.rewardevent.service;

import com.community.audit.dto.AuditActions;
import com.community.operator.service.OperatorMemberAccountService;
import com.community.rewardevent.dto.RewardBulkConfig;
import com.community.rewardevent.dto.RewardMemberRef;
import com.community.rewardevent.dto.RewardOperator;
import com.community.rewardevent.dto.request.RewardCommentPreviewRequest;
import com.community.rewardevent.dto.request.RewardDrawRequest;
import com.community.rewardevent.dto.request.RewardRecipientAddRequest;
import com.community.rewardevent.dto.request.RewardTargetPreviewRequest;
import com.community.rewardevent.dto.response.RewardCommentPreviewView;
import com.community.rewardevent.dto.response.RewardDrawView;
import com.community.rewardevent.dto.response.RewardEventDetailView;
import com.community.rewardevent.dto.response.RewardRecipientResolveView;
import com.community.rewardevent.dto.response.RewardTargetPreviewView;
import com.community.rewardevent.entity.RewardBulkScope;
import com.community.rewardevent.entity.RewardEventDrawEntity;
import com.community.rewardevent.entity.RewardEventEntity;
import com.community.rewardevent.entity.RewardEventLineResultEntity;
import com.community.rewardevent.entity.RewardEventMode;
import com.community.rewardevent.entity.RewardEventRecipientEntity;
import com.community.rewardevent.entity.RewardEventStatus;
import com.community.rewardevent.entity.RewardRecipientMethod;
import com.community.rewardevent.entity.RewardRecipientStatus;
import com.community.rewardevent.exception.RewardEventConflictException;
import com.community.rewardevent.exception.RewardPostNotFoundException;
import com.community.rewardevent.exception.RewardRecipientNotFoundException;
import com.community.rewardevent.repository.master.RewardEventStore;
import com.community.rewardevent.repository.master.RewardEventStore.LineRow;
import com.community.rewardevent.repository.master.RewardEventStore.TierRow;
import com.community.rewardevent.repository.master.RewardMemberQueries;
import com.community.rewardevent.repository.master.RewardMemberQueries.BulkCondition;
import com.community.rewardevent.repository.master.RewardMemberQueries.MemberRow;
import com.community.rewardevent.repository.master.RewardMemberQueries.PostRow;
import com.community.rewardevent.repository.master.RewardRecipientStore;
import com.community.rewardevent.repository.master.RewardRecipientStore.RecipientRow;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대상 고르기(event-admin-contract 「대상 규칙」) — 후보 미리 보기(댓글·일괄, 저장 전에도) · 붙여 넣기 해석 · 당첨자 추가·빼기 · 댓글 추첨.
 *
 * <ul>
 *   <li>같은 회원은 이벤트당 한 번(유일 {@code (event_id, user_id)} — 서비스가 먼저 거르고 색인이 끝까지 막는다). 정지·탈퇴는 늘 빼고, 운영자 회원 계정은 {@code excludeOperators} 일 때 뺀다.</li>
 *   <li>당첨자 추가·빼기는 지급 전(대기 줄만 — 지급한 사람은 {@code 409 REWARD_RECIPIENT_GRANTED}), 등수 자리를 넘으면 {@code 409 REWARD_EVENT_TIER_FULL}.</li>
 *   <li><b>추첨</b>은 서버가 한다: 64비트 난수 추첨 번호(seed)를 정하고, 그 글의 기간 안 댓글 작성자(1인 1회 · 지운 댓글 제외 · 글 작성자 제외 · 위 제외 · 이미 이 이벤트에 있는 회원 제외)를
 *       회원 번호 오름차순으로 세워 그 번호의 난수로 섞은 앞 N명({@link RewardEventRules#draw}). 마감 뒤에만, 후보가 모자라면 {@code 409 REWARD_DRAW_NOT_ENOUGH}.</li>
 * </ul>
 */
@Service
public class RewardSelectionService {

    private final RewardEventStore events;
    private final RewardRecipientStore recipients;
    private final RewardMemberQueries members;
    private final OperatorMemberAccountService operatorAccounts;
    private final RewardEventSupport support;
    private final RewardEventViews views;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;
    private final SecureRandom seeds = new SecureRandom();

    public RewardSelectionService(RewardEventStore events, RewardRecipientStore recipients, RewardMemberQueries members, OperatorMemberAccountService operatorAccounts,
                                  RewardEventSupport support, RewardEventViews views, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.events = events;
        this.recipients = recipients;
        this.members = members;
        this.operatorAccounts = operatorAccounts;
        this.support = support;
        this.views = views;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 미리 보기 ───────────────────────────────────────────────────────────

    /** 후보 분류 결과 */
    record Candidates(PostRow post, long commentCount, List<Long> candidates, long author, long operators, long suspended, long withdrawn) {
    }

    /** 그 글의 기간 안 댓글 작성자를 1인 1회로 세우고 나눈다 — 글 작성자 → 탈퇴 → 정지 → 운영자 회원 계정 → (이 이벤트에 이미 있음) 차례로 뺀다 */
    Candidates candidates(PostRow post, Instant from, Instant to, boolean excludeAuthor, boolean excludeOperators, Long eventId) {
        List<Long> commenters = members.commenters(post.id(), from, to);
        Map<Long, MemberRow> people = members.members(commenters);
        Set<Long> operators = excludeOperators ? operatorAccounts.allActiveOperatorUserIds() : Set.of();
        Set<Long> already = eventId == null ? Set.of() : recipients.existingUsers(eventId, commenters);
        Set<Long> ai = members.aiUserIds(); // AI 회원은 추첨 후보가 아니다(ai-members-contract 정책 10) — 빼고 세지 않는다
        List<Long> out = new ArrayList<>();
        long author = 0;
        long ops = 0;
        long suspended = 0;
        long withdrawn = 0;
        for (Long u : commenters) {
            MemberRow m = people.get(u);
            if (excludeAuthor && u.equals(post.authorId())) {
                author++;
            } else if (m == null || m.withdrawn()) {
                withdrawn++;
            } else if (m.suspended()) {
                suspended++;
            } else if (ai.contains(u)) {
                continue;
            } else if (operators.contains(u)) {
                ops++;
            } else if (!already.contains(u)) {
                out.add(u);
            }
        }
        return new Candidates(post, members.commentCount(post.id(), from, to), out, author, ops, suspended, withdrawn);
    }

    @Transactional(readOnly = true)
    public RewardCommentPreviewView previewComments(RewardCommentPreviewRequest body) {
        Optional<Long> postId = RewardEventRules.parsePostRef(body == null ? null : body.postRef());
        if (postId.isEmpty()) {
            throw ValidationException.of("postRef", new MessageRef("rewardEvent.postRef.invalid"));
        }
        PostRow post = members.post(postId.get()).orElseThrow(RewardPostNotFoundException::new);
        Instant from = body.from() == null ? Instant.EPOCH : body.from();
        Instant to = body.to() == null ? now() : body.to();
        Candidates c = candidates(post, from, to, body.excludeAuthor() == null || body.excludeAuthor(), body.excludeOperators() == null || body.excludeOperators(), null);
        return new RewardCommentPreviewView(Long.toString(post.id()), post.title(), post.authorNickname(), c.commentCount(), c.candidates().size(),
                new RewardCommentPreviewView.Excluded(c.author(), c.operators(), c.suspended(), c.withdrawn()));
    }

    @Transactional(readOnly = true)
    public RewardTargetPreviewView previewTargets(RewardTargetPreviewRequest body) {
        RewardBulkConfig bulk = body == null ? null : body.bulk();
        RewardBulkScope scope = bulk == null ? null : RewardEventEditService.enumOf(RewardBulkScope.class, bulk.scope());
        if (scope == null) {
            throw ValidationException.of("bulk.scope", new MessageRef("rewardEvent.bulk.scope.invalid"));
        }
        if (bulk.activeWithinDays() != null && (bulk.activeWithinDays() < 1 || bulk.activeWithinDays() > RewardEventRules.ACTIVE_DAYS_MAX)) {
            throw ValidationException.of("bulk.activeWithinDays", new MessageRef("rewardEvent.bulk.activeWithinDays.range", RewardEventRules.ACTIVE_DAYS_MAX));
        }
        boolean excludeOperators = body.excludeOperators() == null || body.excludeOperators();
        Set<Long> operators = operatorAccounts.allActiveOperatorUserIds();
        BulkCondition c = new BulkCondition(scope == RewardBulkScope.ALL, bulk.minGradeLevel(), bulk.joinedFrom(), bulk.joinedTo(), Boolean.TRUE.equals(bulk.identityVerifiedOnly()),
                bulk.activeWithinDays());
        Instant now = now();
        return new RewardTargetPreviewView(members.countBulkTargets(c, excludeOperators ? operators : Set.of(), now), members.countNotActive(),
                excludeOperators ? members.countActive(operators) : 0);
    }

    // ── 붙여 넣기 · 추가 · 빼기 ─────────────────────────────────────────────

    /** 붙여 넣은 줄 → 찾은 회원 / 못 찾은 줄 / 이미 있음 / 넣을 수 없음(같은 줄·같은 회원은 한 번, 500줄까지) */
    @Transactional(readOnly = true)
    public RewardRecipientResolveView resolve(long eventId, List<String> lines, UnaryOperator<String> emailView) {
        RewardEventEntity e = events.event(eventId).orElseThrow(com.community.rewardevent.exception.RewardEventNotFoundException::new);
        Set<String> uniq = new LinkedHashSet<>();
        for (String line : lines == null ? List.<String>of() : lines) {
            if (line != null && !line.isBlank()) {
                uniq.add(line.strip());
            }
            if (uniq.size() >= RewardEventRules.BATCH_MAX) {
                break;
            }
        }
        Set<Long> operators = e.isExcludeOperators() ? operatorAccounts.allActiveOperatorUserIds() : Set.of();
        List<RewardMemberRef> found = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        List<RewardMemberRef> alreadyIn = new ArrayList<>();
        List<RewardRecipientResolveView.Excluded> excluded = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (String raw : uniq) {
            Optional<MemberRow> m = members.resolve(raw);
            if (m.isEmpty()) {
                notFound.add(raw);
                continue;
            }
            MemberRow row = m.get();
            if (!seen.add(row.id())) {
                continue;
            }
            RewardMemberRef ref = RewardEventViews.member(row.id(), row, emailView);
            if (recipients.exists(eventId, row.id())) {
                alreadyIn.add(ref);
            } else if (row.withdrawn()) {
                excluded.add(new RewardRecipientResolveView.Excluded(ref, "WITHDRAWN"));
            } else if (row.suspended()) {
                excluded.add(new RewardRecipientResolveView.Excluded(ref, "SUSPENDED"));
            } else if (operators.contains(row.id())) {
                excluded.add(new RewardRecipientResolveView.Excluded(ref, "OPERATOR"));
            } else {
                found.add(ref);
            }
        }
        return new RewardRecipientResolveView(found, notFound, alreadyIn, excluded);
    }

    /**
     * 당첨자 추가(직접 입력이 후보 방법일 때 · 준비·진행 중·마감 · 나눠 처리 중이 아닐 때) — 이미 있는 회원·정지·탈퇴·(빼기 옵션) 운영자 회원 계정은 조용히 거르고, 남은 수가 등수 자리를
     * 넘으면 {@code 409 REWARD_EVENT_TIER_FULL}.
     */
    @Transactional
    public RewardEventDetailView addRecipients(RewardOperator op, long eventId, RewardRecipientAddRequest body) {
        Instant now = now();
        RewardEventEntity e = support.lockFresh(eventId, now);
        if (e.getMode() != RewardEventMode.SELECTION || !e.isSourceManual() || !RewardEventRules.open(e.getStatus()) || events.runningRun(eventId).isPresent()) {
            throw new RewardEventConflictException(RewardEventConflictException.STATE);
        }
        TierRow tier = tierOf(eventId, body == null ? null : body.tierId());
        List<Long> ids0 = new ArrayList<>(new LinkedHashSet<>((body.memberIds() == null ? List.<String>of() : body.memberIds()).stream()
                .map(RewardEventRules::parseId).flatMap(Optional::stream).toList()));
        if (ids0.isEmpty() || ids0.size() > RewardEventRules.BATCH_MAX) {
            throw ValidationException.of("memberIds", new MessageRef("rewardEvent.memberIds.range", RewardEventRules.BATCH_MAX));
        }
        Map<Long, MemberRow> people = members.members(ids0);
        Set<Long> operators = e.isExcludeOperators() ? operatorAccounts.allActiveOperatorUserIds() : Set.of();
        Set<Long> already = recipients.existingUsers(eventId, ids0);
        List<Long> add = ids0.stream().filter(u -> people.containsKey(u) && people.get(u).active() && !operators.contains(u) && !already.contains(u)).toList();
        long inTier = recipients.countByTier(eventId).getOrDefault(tier.id(), 0L);
        if (inTier + add.size() > tier.quota()) {
            throw new RewardEventConflictException(RewardEventConflictException.TIER_FULL,
                    new MessageRef("error.REWARD_EVENT_TIER_FULL", tier.quota(), Math.max(0, tier.quota() - inTier)));
        }
        insertRecipients(eventId, tier, add, RewardRecipientMethod.MANUAL, op.id(), null, now);
        support.record(op.id(), op.ip(), AuditActions.REWARD_EVENT_RECIPIENT_ADD, eventId, Map.of("tierId", Long.toString(tier.id()), "count", add.size(),
                "userIds", add.stream().limit(50).map(String::valueOf).toList()));
        return views.detail(events.event(eventId).orElseThrow(), now);
    }

    /** 당첨자 빼기 — 지급 전(대기 줄)만, 열린 이벤트·나눠 처리 중이 아닐 때 */
    @Transactional
    public RewardEventDetailView removeRecipient(RewardOperator op, long eventId, long recipientId) {
        Instant now = now();
        RewardEventEntity e = support.lockFresh(eventId, now);
        RecipientRow r = recipients.recipient(recipientId).filter(x -> x.eventId() == eventId).orElseThrow(RewardRecipientNotFoundException::new);
        if (r.status() != RewardRecipientStatus.PENDING || r.queuedRunId() != null || !RewardEventRules.open(e.getStatus())) {
            throw new RewardEventConflictException(RewardEventConflictException.RECIPIENT_GRANTED);
        }
        recipients.delete(recipientId);
        support.record(op.id(), op.ip(), AuditActions.REWARD_EVENT_RECIPIENT_REMOVE, eventId, Map.of("recipientId", Long.toString(recipientId),
                "userId", Long.toString(r.userId()), "tierId", Long.toString(r.tierId())));
        support.settleDone(eventId, now);
        return views.detail(events.event(eventId).orElseThrow(), now);
    }

    // ── 추첨 ────────────────────────────────────────────────────────────────

    /** 댓글 추첨 — 마감 뒤 · 댓글 추첨이 후보 방법 · 나눠 처리 중이 아닐 때. 뽑을 수는 1 ~ 그 등수의 남은 자리 */
    @Transactional
    public RewardDrawView draw(RewardOperator op, long eventId, RewardDrawRequest body, UnaryOperator<String> emailView) {
        Instant now = now();
        RewardEventEntity e = support.lockFresh(eventId, now);
        if (e.getMode() != RewardEventMode.SELECTION || !e.isSourceComment()) {
            throw new RewardEventConflictException(RewardEventConflictException.NO_COMMENT_SOURCE);
        }
        if (e.getStatus() != RewardEventStatus.CLOSED || events.runningRun(eventId).isPresent()) {
            throw new RewardEventConflictException(RewardEventConflictException.STATE);
        }
        TierRow tier = tierOf(eventId, body == null ? null : body.tierId());
        long room = tier.quota() - recipients.countByTier(eventId).getOrDefault(tier.id(), 0L);
        Integer count = body.count();
        if (count == null || count < 1 || count > room) {
            throw ValidationException.of("count", new MessageRef("rewardEvent.count.range", Math.max(0, room)));
        }
        PostRow post = members.post(e.getDrawPostId()).orElseThrow(RewardPostNotFoundException::new);
        Candidates c = candidates(post, e.getDrawFrom(), e.getDrawTo(), e.isDrawExcludeAuthor(), e.isExcludeOperators(), eventId);
        if (c.candidates().size() < count) {
            throw new RewardEventConflictException(RewardEventConflictException.DRAW_NOT_ENOUGH,
                    new MessageRef("error.REWARD_DRAW_NOT_ENOUGH", c.candidates().size(), count));
        }
        long seed = seeds.nextLong();
        List<Long> picked = RewardEventRules.draw(c.candidates(), seed, count);
        long drawId = ids.nextId();
        events.insertDraw(RewardEventDrawEntity.create(drawId, eventId, tier.id(), op.id(), post.id(), c.candidates().size(), seed, picked.toArray(Long[]::new), now));
        insertRecipients(eventId, tier, picked, RewardRecipientMethod.COMMENT_DRAW, op.id(), drawId, now);
        support.record(op.id(), op.ip(), AuditActions.REWARD_EVENT_DRAW, eventId, Map.of("drawId", Long.toString(drawId), "tierId", Long.toString(tier.id()),
                "postId", Long.toString(post.id()), "candidateCount", c.candidates().size(), "picked", picked.size(), "seed", RewardEventRules.seedText(seed)));
        Map<Long, MemberRow> people = members.members(picked);
        Map<Long, String> names = members.operatorNames(List.of(op.id()));
        return new RewardDrawView(Long.toString(drawId), now, RewardEventViews.operator(op.id(), names), Long.toString(post.id()), post.title(), Long.toString(tier.id()),
                tier.name(), c.candidates().size(), picked.size(), RewardEventRules.seedText(seed),
                picked.stream().map(u -> RewardEventViews.member(u, people.get(u), emailView)).toList());
    }

    private TierRow tierOf(long eventId, String tierId) {
        return RewardEventRules.parseId(tierId).flatMap(t -> events.tiers(eventId).stream().filter(x -> x.id() == t).findFirst())
                .orElseThrow(() -> ValidationException.of("tierId", new MessageRef("rewardEvent.tierId.required")));
    }

    /** 받는 사람(대기) + 그 등수의 보상 줄마다 대기 줄 결과 */
    private void insertRecipients(long eventId, TierRow tier, List<Long> userIds, RewardRecipientMethod method, Long operatorId, Long drawId, Instant now) {
        List<LineRow> lines = events.lines(eventId).stream().filter(l -> l.tierId() == tier.id()).toList();
        List<RewardEventRecipientEntity> rows = new ArrayList<>();
        List<RewardEventLineResultEntity> results = new ArrayList<>();
        for (Long u : userIds) {
            long rid = ids.nextId();
            rows.add(RewardEventRecipientEntity.pending(rid, eventId, tier.id(), u, method, now, operatorId, drawId, null));
            for (LineRow l : lines) {
                results.add(RewardEventLineResultEntity.pending(ids.nextId(), eventId, rid, l.id(), u, l.kind()));
            }
        }
        recipients.insert(rows, results);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
