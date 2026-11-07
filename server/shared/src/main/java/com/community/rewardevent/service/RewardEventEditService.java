package com.community.rewardevent.service;

import com.community.audit.dto.AuditActions;
import com.community.emoticon.entity.EmoticonPackSource;
import com.community.emoticon.entity.EmoticonPackStatus;
import com.community.emoticon.repository.master.EmoticonPackStore;
import com.community.emoticon.repository.master.EmoticonPackStore.PackRow;
import com.community.point.service.PointPolicy;
import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopUsageType;
import com.community.pointshop.repository.master.PointShopItemStore;
import com.community.pointshop.repository.master.PointShopItemStore.ItemRow;
import com.community.rewardevent.dto.RewardBulkConfig;
import com.community.rewardevent.dto.RewardCommentDrawConfig;
import com.community.rewardevent.dto.RewardLineDto;
import com.community.rewardevent.dto.RewardOperator;
import com.community.rewardevent.dto.RewardSelectionConfig;
import com.community.rewardevent.dto.RewardTierDto;
import com.community.rewardevent.dto.request.RewardEventSaveRequest;
import com.community.rewardevent.dto.response.RewardEventDetailView;
import com.community.rewardevent.entity.RewardBulkScope;
import com.community.rewardevent.entity.RewardEditScope;
import com.community.rewardevent.entity.RewardEventEntity;
import com.community.rewardevent.entity.RewardEventLineEntity;
import com.community.rewardevent.entity.RewardEventLineResultEntity;
import com.community.rewardevent.entity.RewardEventMode;
import com.community.rewardevent.entity.RewardEventStatus;
import com.community.rewardevent.entity.RewardEventTierEntity;
import com.community.rewardevent.entity.RewardEventTrigger;
import com.community.rewardevent.entity.RewardKind;
import com.community.rewardevent.exception.RewardEventConflictException;
import com.community.rewardevent.exception.RewardLargeRequiredException;
import com.community.rewardevent.repository.master.RewardEventStore;
import com.community.rewardevent.repository.master.RewardEventStore.LineRow;
import com.community.rewardevent.repository.master.RewardEventStore.TierRow;
import com.community.rewardevent.repository.master.RewardMemberQueries;
import com.community.rewardevent.repository.master.RewardRecipientStore;
import com.community.rewardevent.repository.master.RewardRecipientStore.RecipientRow;
import com.community.rewardevent.repository.master.RewardRecipientStore.RecipientFilter;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보상 이벤트 만들기·고치기·시작·마감·취소(event-admin-contract 「상태·방식」). 한 트랜잭션, 이벤트 행 잠금이 첫 문장이다({@link RewardEventSupport#lockFresh}).
 *
 * <ul>
 *   <li><b>검사</b>: 칸을 모아 {@code 400 VALIDATION}(칸 경로 {@code tiers[0].rewards[1].points} — 화면의 목업 {@code validate} 와 같은 차례). 품목(아바타 제외 · 판매 중단도 가능)·
 *       팩(판매 중 · 운영 팩·무료 팩만 — 작가 유료 팩은 정산 때문에 400)·글(댓글 추첨)을 서버가 찾아 이름·종류·사용 방식·기간 사본을 채운다.</li>
 *   <li><b>고치기 범위</b>({@link RewardEventRules#editScope}): 범위 밖 칸이 바뀌면 {@code 409 REWARD_EVENT_LOCKED_FIELD}(그 칸 경로). 인원은 이미 받은(선정된) 수 아래로 못 줄인다
 *       ({@code 400 fields.firstCome.limit | tiers[i].quota}). 준비 상태의 고치기는 등수·보상 줄을 다시 만든다 — 빠진 등수의 당첨자는 빠지고, 남은 당첨자의 줄 결과는 새 줄로 다시 만든다.</li>
 *   <li><b>고액</b>: 선착순 자동 지급은 만들기·고치기(준비)·시작 때 1인 포인트 합이 고액 문턱을 넘으면 그 운영자에게 {@code point.adjust.large} 가 있어야 한다(없으면 거부 감사 + 403).</li>
 *   <li>상태 전이는 조건부 UPDATE. 감사: 만듦·고침(바뀐 칸 전→후)·시작·마감·취소(사유).</li>
 * </ul>
 */
@Service
public class RewardEventEditService {

    private final RewardEventStore events;
    private final RewardRecipientStore recipients;
    private final RewardMemberQueries members;
    private final PointShopItemStore items;
    private final EmoticonPackStore packs;
    private final PointPolicy policy;
    private final RewardEventSupport support;
    private final RewardEventViews views;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public RewardEventEditService(RewardEventStore events, RewardRecipientStore recipients, RewardMemberQueries members, PointShopItemStore items, EmoticonPackStore packs,
                                  PointPolicy policy, RewardEventSupport support, RewardEventViews views, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.events = events;
        this.recipients = recipients;
        this.members = members;
        this.items = items;
        this.packs = packs;
        this.policy = policy;
        this.support = support;
        this.views = views;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 검사를 마친 저장 값 — 이벤트 칸 + 등수(요청 번호 · 이름 · 인원 · 줄 값) */
    record Spec(RewardEventEntity.Values values, List<TierSpec> tiers) {
    }

    record TierSpec(String requestId, String name, int quota, List<LineSpec> lines) {
    }

    record LineSpec(String requestId, RewardEventLineEntity.Values values) {
    }

    // ── 만들기 · 고치기 ─────────────────────────────────────────────────────

    @Transactional
    public RewardEventDetailView create(RewardOperator op, RewardEventSaveRequest body) {
        Instant now = now();
        Spec spec = validate(body, null);
        requireLarge(op, spec);
        long id = ids.nextId();
        events.insert(RewardEventEntity.create(id, events.nextNo(), spec.values(), op.id(), now));
        insertStructure(id, spec, Map.of());
        support.record(op.id(), op.ip(), AuditActions.REWARD_EVENT_CREATE, id, Map.of("name", spec.values().name(), "mode", spec.values().mode().name(),
                "tiers", spec.tiers().size(), "lines", spec.tiers().stream().mapToInt(t -> t.lines().size()).sum()));
        return views.detail(events.event(id).orElseThrow(), now);
    }

    @Transactional
    public RewardEventDetailView update(RewardOperator op, long id, RewardEventSaveRequest body) {
        Instant now = now();
        RewardEventEntity current = support.lockFresh(id, now);
        RewardEditScope scope = RewardEventRules.editScope(current.getStatus());
        List<TierRow> tiers = events.tiers(id);
        List<LineRow> lines = events.lines(id);
        Spec spec = validate(body, current);
        if (scope != RewardEditScope.FULL) {
            String locked = lockedChange(scope, current, tiers, lines, spec);
            if (locked != null) {
                throw RewardEventConflictException.lockedField(locked);
            }
        }
        checkQuotas(current, tiers, spec);
        if (scope == RewardEditScope.FULL) {
            requireLarge(op, spec);
        }
        Map<String, Object> before = summary(current, tiers);
        RewardEventEntity editable = events.eventForEdit(id).orElseThrow();
        RewardEventEntity.Values values = scope == RewardEditScope.MEMO ? keepAllBut(current, spec.values().memo()) : spec.values();
        editable.edit(values, op.id(), now);
        events.saveEdit(editable);
        if (scope == RewardEditScope.FULL) {
            rebuildStructure(id, tiers, spec);
        } else if (scope == RewardEditScope.ACTIVE || scope == RewardEditScope.CLOSED) {
            for (int i = 0; i < spec.tiers().size(); i++) {
                TierSpec t = spec.tiers().get(i);
                events.updateTierQuota(tiers.get(i).id(), t.quota());
            }
        }
        RewardEventEntity after = events.event(id).orElseThrow();
        Map<String, Object> afterSummary = summary(after, events.tiers(id));
        Map<String, Object> changedBefore = new LinkedHashMap<>();
        Map<String, Object> changedAfter = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : afterSummary.entrySet()) {
            if (!Objects.equals(before.get(e.getKey()), e.getValue())) {
                changedBefore.put(e.getKey(), before.get(e.getKey()));
                changedAfter.put(e.getKey(), e.getValue());
            }
        }
        if (scope == RewardEditScope.FULL && !structureEquals(tiers, lines, spec)) {
            changedAfter.put("structure", "REBUILT");
        }
        support.record(op.id(), op.ip(), AuditActions.REWARD_EVENT_UPDATE, id, Map.of("scope", scope.name(), "before", changedBefore, "after", changedAfter));
        return views.detail(after, now);
    }

    /** 준비 상태의 고치기 — 줄 결과 → 보상 줄 → 빠진 등수의 당첨자 → 빠진 등수를 지우고, 남은 등수는 고치고 새 등수·줄을 넣은 뒤 남은 당첨자의 줄 결과를 다시 만든다 */
    private void rebuildStructure(long eventId, List<TierRow> existing, Spec spec) {
        Map<String, Long> keep = new HashMap<>();
        Set<Long> existingIds = new LinkedHashSet<>();
        existing.forEach(t -> existingIds.add(t.id()));
        for (TierSpec t : spec.tiers()) {
            RewardEventRules.parseId(t.requestId()).filter(existingIds::contains).ifPresent(tid -> keep.put(t.requestId(), tid));
        }
        List<Long> removed = existing.stream().map(TierRow::id).filter(tid -> !keep.containsValue(tid)).toList();
        recipients.deleteResultsOf(eventId);
        events.deleteLines(eventId);
        // 당첨자 선정이 아니게 바뀌면 직접 넣은 당첨자는 모두 뺀다(선착순·일괄은 지급 때 대상이 정해진다)
        recipients.deleteOfTiers(spec.values().mode() == RewardEventMode.SELECTION ? removed : List.copyOf(existingIds));
        events.deleteTiers(removed);
        insertStructure(eventId, spec, keep);
        // 남은 당첨자(준비 상태라 모두 대기)의 줄 결과를 새 보상 줄로 다시 만든다
        Map<Long, List<LineRow>> byTier = new HashMap<>();
        for (LineRow l : events.lines(eventId)) {
            byTier.computeIfAbsent(l.tierId(), k -> new ArrayList<>()).add(l);
        }
        List<RewardEventLineResultEntity> results = new ArrayList<>();
        int page = 0;
        while (true) {
            List<RecipientRow> rows = recipients.page(new RecipientFilter(eventId, null, null, null, null), page++, 500);
            if (rows.isEmpty()) {
                break;
            }
            for (RecipientRow r : rows) {
                for (LineRow l : byTier.getOrDefault(r.tierId(), List.of())) {
                    results.add(RewardEventLineResultEntity.pending(ids.nextId(), eventId, r.id(), l.id(), r.userId(), l.kind()));
                }
            }
        }
        recipients.insertResults(results);
    }

    /** 등수·줄 넣기 — {@code keep}(요청 번호 → 남기는 등수 번호)은 고치고, 나머지는 새로 */
    private void insertStructure(long eventId, Spec spec, Map<String, Long> keep) {
        List<RewardEventTierEntity> newTiers = new ArrayList<>();
        List<RewardEventLineEntity> newLines = new ArrayList<>();
        for (int i = 0; i < spec.tiers().size(); i++) {
            TierSpec t = spec.tiers().get(i);
            Long tierId = keep.get(t.requestId());
            if (tierId == null) {
                tierId = ids.nextId();
                newTiers.add(RewardEventTierEntity.create(tierId, eventId, i, t.name(), t.quota()));
            } else {
                events.updateTier(tierId, i, t.name(), t.quota());
            }
            for (int j = 0; j < t.lines().size(); j++) {
                newLines.add(RewardEventLineEntity.create(ids.nextId(), eventId, tierId, j, t.lines().get(j).values()));
            }
        }
        events.insertTiers(newTiers);
        events.insertLines(newLines);
    }

    /** 인원은 이미 받은(선정된) 수 아래로 줄일 수 없다 */
    private void checkQuotas(RewardEventEntity current, List<TierRow> tiers, Spec spec) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        if (spec.values().mode() == RewardEventMode.FIRST_COME && current.getMode() == RewardEventMode.FIRST_COME
                && spec.values().firstComeLimit() < current.getFirstComeCount()) {
            fields.put("firstCome.limit", new MessageRef("rewardEvent.quota.belowReceived", String.format("%,d", current.getFirstComeCount())));
        }
        if (spec.values().mode() == RewardEventMode.SELECTION) {
            Map<Long, Long> byTier = recipients.countByTier(current.getId());
            for (int i = 0; i < spec.tiers().size(); i++) {
                Optional<Long> tid = RewardEventRules.parseId(spec.tiers().get(i).requestId());
                long have = tid.map(t -> byTier.getOrDefault(t, 0L)).orElse(0L);
                if (spec.tiers().get(i).quota() < have) {
                    fields.put("tiers[" + i + "].quota", new MessageRef("rewardEvent.quota.belowSelected", String.valueOf(have)));
                }
            }
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
    }

    /** 고치기 범위 밖에서 바뀐 첫 칸(없으면 null) — 확정 화면의 목업 {@code lockedChanged} 와 같은 차례 */
    static String lockedChange(RewardEditScope scope, RewardEventEntity c, List<TierRow> tiers, List<LineRow> lines, Spec spec) {
        RewardEventEntity.Values v = spec.values();
        if (scope != RewardEditScope.ACTIVE && !v.name().equals(c.getName())) {
            return "name";
        }
        if (!v.startAt().equals(c.getStartAt())) {
            return "startAt";
        }
        if (scope != RewardEditScope.ACTIVE && !v.endAt().equals(c.getEndAt())) {
            return "endAt";
        }
        if (scope == RewardEditScope.MEMO && !v.noticeUrl().equals(c.getNoticeUrl())) {
            return "noticeUrl";
        }
        // 댓글 기간은 끝 시각 규칙으로 본다(이벤트 기간과 같게면 끝 시각을 고칠 때 같이 바뀐다)
        if (v.mode() != c.getMode() || v.sourceManual() != c.isSourceManual() || v.sourceComment() != c.isSourceComment()
                || !Objects.equals(v.drawPostId(), c.getDrawPostId()) || v.sourceComment() && v.drawExcludeAuthor() != c.isDrawExcludeAuthor()
                || v.bulkScope() != c.getBulkScope() || !Objects.equals(v.bulkMinGrade(), c.getBulkMinGrade()) || !Objects.equals(v.bulkJoinedFrom(), c.getBulkJoinedFrom())
                || !Objects.equals(v.bulkJoinedTo(), c.getBulkJoinedTo()) || v.bulkIdentityOnly() != c.isBulkIdentityOnly()
                || !Objects.equals(v.bulkActiveDays(), c.getBulkActiveDays()) || v.excludeOperators() != c.isExcludeOperators()) {
            return "mode";
        }
        if (v.firstComeTrigger() != c.getFirstComeTrigger()) {
            return "firstCome.trigger";
        }
        if (scope != RewardEditScope.ACTIVE && !Objects.equals(v.firstComeLimit(), c.getFirstComeLimit())) {
            return "firstCome.limit";
        }
        if (!structureEquals(tiers, lines, spec)) {
            return "tiers";
        }
        if (scope == RewardEditScope.MEMO) {
            for (int i = 0; i < tiers.size(); i++) {
                if (tiers.get(i).quota() != spec.tiers().get(i).quota()) {
                    return "tiers";
                }
            }
        }
        return null;
    }

    /** 등수 번호·이름·보상 줄(번호·종류·값)이 같은가(인원은 보지 않는다) */
    static boolean structureEquals(List<TierRow> tiers, List<LineRow> lines, Spec spec) {
        if (tiers.size() != spec.tiers().size()) {
            return false;
        }
        Map<Long, List<LineRow>> byTier = new HashMap<>();
        lines.forEach(l -> byTier.computeIfAbsent(l.tierId(), k -> new ArrayList<>()).add(l));
        for (int i = 0; i < tiers.size(); i++) {
            TierRow t = tiers.get(i);
            TierSpec s = spec.tiers().get(i);
            if (!Long.toString(t.id()).equals(s.requestId()) || !t.name().equals(s.name())) {
                return false;
            }
            List<LineRow> have = byTier.getOrDefault(t.id(), List.of());
            if (have.size() != s.lines().size()) {
                return false;
            }
            for (int j = 0; j < have.size(); j++) {
                LineRow l = have.get(j);
                LineSpec ls = s.lines().get(j);
                RewardEventLineEntity.Values lv = ls.values();
                if (!Long.toString(l.id()).equals(ls.requestId()) || l.kind() != lv.kind() || !Objects.equals(l.points(), lv.points())
                        || !Objects.equals(l.itemId(), lv.itemId()) || !Objects.equals(l.quantity(), lv.quantity()) || !Objects.equals(l.packId(), lv.packId())
                        || !Objects.equals(l.manualName(), lv.manualName()) || !Objects.equals(l.manualMethod(), lv.manualMethod())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static RewardEventEntity.Values keepAllBut(RewardEventEntity c, String memo) {
        return new RewardEventEntity.Values(c.getName(), memo, c.getNoticeUrl(), c.getMode(), c.getStartAt(), c.getEndAt(), c.isExcludeOperators(), c.getFirstComeTrigger(),
                c.getFirstComeLimit(), c.isSourceManual(), c.isSourceComment(), c.getDrawPostRef(), c.getDrawPostId(), c.getDrawFrom(), c.getDrawTo(), c.isDrawExcludeAuthor(),
                c.getBulkScope(), c.getBulkMinGrade(), c.getBulkJoinedFrom(), c.getBulkJoinedTo(), c.isBulkIdentityOnly(), c.getBulkActiveDays());
    }

    private static Map<String, Object> summary(RewardEventEntity e, List<TierRow> tiers) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", e.getName());
        out.put("memoLength", RewardEventRules.length(e.getMemo()));
        out.put("noticeUrl", e.getNoticeUrl());
        out.put("startAt", e.getStartAt().toString());
        out.put("endAt", e.getEndAt().toString());
        out.put("firstComeLimit", e.getFirstComeLimit());
        out.put("quotas", tiers.stream().map(TierRow::quota).toList());
        return out;
    }

    // ── 시작 · 마감 · 취소 ──────────────────────────────────────────────────

    /** 시작(준비 → 진행 중) — 끝 시각 전만({@code 409 REWARD_EVENT_PERIOD_PASSED}). 선착순은 시작하는 운영자 기준 고액 검사 */
    @Transactional
    public RewardEventDetailView start(RewardOperator op, long id) {
        Instant now = now();
        RewardEventEntity e = support.lockFresh(id, now);
        if (e.getStatus() != RewardEventStatus.DRAFT) {
            throw new RewardEventConflictException(RewardEventConflictException.STATE);
        }
        if (!e.getEndAt().isAfter(now)) {
            throw new RewardEventConflictException(RewardEventConflictException.PERIOD_PASSED);
        }
        if (e.getMode() == RewardEventMode.FIRST_COME) {
            requireLarge(op, e.getMode(), RewardEventRules.pointsByTier(events.lines(id)).values());
        }
        if (!events.start(id, op.id(), now)) {
            throw new RewardEventConflictException(RewardEventConflictException.STATE);
        }
        support.record(op.id(), op.ip(), AuditActions.REWARD_EVENT_START, id, Map.of("startAt", e.getStartAt().toString(), "endAt", e.getEndAt().toString()));
        return views.detail(events.event(id).orElseThrow(), now);
    }

    /** 운영자 마감(진행 중일 때만) — 선착순은 대기가 없으면 바로 지급 끝 */
    @Transactional
    public RewardEventDetailView close(RewardOperator op, long id) {
        Instant now = now();
        support.lockFresh(id, now);
        if (!events.close(id, op.id(), now)) {
            throw new RewardEventConflictException(RewardEventConflictException.STATE);
        }
        support.record(op.id(), op.ip(), AuditActions.REWARD_EVENT_CLOSE, id, Map.of());
        support.settleDone(id, now);
        return views.detail(events.event(id).orElseThrow(), now);
    }

    /** 취소 — 지급이 하나도 없을 때만({@code 409 REWARD_EVENT_HAS_GRANTS} — 회수를 쓴다), 사유 5~200자 */
    @Transactional
    public RewardEventDetailView cancel(RewardOperator op, long id, String reason) {
        String text = RewardEventRules.normalize(reason);
        if (RewardEventRules.length(text) < RewardEventRules.REASON_MIN || RewardEventRules.length(text) > RewardEventRules.REASON_MAX) {
            throw ValidationException.of("reason", new MessageRef("rewardEvent.reason.length", RewardEventRules.REASON_MIN, RewardEventRules.REASON_MAX));
        }
        Instant now = now();
        RewardEventEntity e = support.lockFresh(id, now);
        if (!RewardEventRules.open(e.getStatus())) {
            throw new RewardEventConflictException(RewardEventConflictException.STATE);
        }
        if (!events.grantedEvents(List.of(id)).isEmpty()) {
            throw new RewardEventConflictException(RewardEventConflictException.HAS_GRANTS);
        }
        if (!events.cancel(id, text, op.id(), now)) {
            throw new RewardEventConflictException(RewardEventConflictException.STATE);
        }
        support.record(op.id(), op.ip(), AuditActions.REWARD_EVENT_CANCEL, id, Map.of("reason", text));
        return views.detail(events.event(id).orElseThrow(), now);
    }

    // ── 고액 ────────────────────────────────────────────────────────────────

    private void requireLarge(RewardOperator op, Spec spec) {
        if (spec.values().mode() != RewardEventMode.FIRST_COME) {
            return;
        }
        List<Long> perTier = spec.tiers().stream().map(t -> t.lines().stream().filter(l -> l.values().kind() == RewardKind.POINT)
                .mapToLong(l -> l.values().points()).sum()).toList();
        requireLarge(op, spec.values().mode(), perTier);
    }

    private void requireLarge(RewardOperator op, RewardEventMode mode, java.util.Collection<Long> perTier) {
        int threshold = policy.lots().adjustLargeThreshold();
        if (RewardEventRules.largeRequired(mode, perTier, threshold) && !op.canLarge()) {
            op.recordLargeDenied().run();
            throw new RewardLargeRequiredException(threshold);
        }
    }

    // ── 검사 ────────────────────────────────────────────────────────────────

    /**
     * 본문 검사 — 칸 경로로 모아 400. 방식에 맞지 않는 칸은 버린다(선착순은 등수 인원 = 인원, 일괄은 0, 당첨자 선정은 등수 인원 1~100,000).
     *
     * @param current 고치기면 지금 이벤트(댓글 기간 기본값·검사에 쓰지 않는다 — 비어 있으면 만들기)
     */
    Spec validate(RewardEventSaveRequest body, RewardEventEntity current) {
        Map<String, MessageRef> f = new LinkedHashMap<>();
        if (body == null) {
            throw ValidationException.of("name", new MessageRef("rewardEvent.name.length", RewardEventRules.NAME_MAX));
        }
        String name = RewardEventRules.normalize(body.name());
        if (RewardEventRules.length(name) < 1 || RewardEventRules.length(name) > RewardEventRules.NAME_MAX) {
            f.put("name", new MessageRef("rewardEvent.name.length", RewardEventRules.NAME_MAX));
        }
        String memo = body.memo() == null ? "" : body.memo().strip();
        if (RewardEventRules.length(memo) > RewardEventRules.MEMO_MAX) {
            f.put("memo", new MessageRef("rewardEvent.memo.max", RewardEventRules.MEMO_MAX));
        }
        Instant startAt = body.startAt() == null ? null : body.startAt().truncatedTo(ChronoUnit.MICROS);
        Instant endAt = body.endAt() == null ? null : body.endAt().truncatedTo(ChronoUnit.MICROS);
        if (startAt == null) {
            f.put("startAt", new MessageRef("rewardEvent.startAt.required"));
        }
        if (endAt == null) {
            f.put("endAt", new MessageRef("rewardEvent.endAt.required"));
        } else if (startAt != null && !endAt.isAfter(startAt)) {
            f.put("endAt", new MessageRef("rewardEvent.endAt.order"));
        }
        String noticeUrl = body.noticeUrl() == null ? "" : body.noticeUrl().strip();
        if (!noticeUrl.isEmpty() && (noticeUrl.length() > RewardEventRules.NOTICE_URL_MAX
                || !(noticeUrl.startsWith("https://") || noticeUrl.startsWith("http://") || noticeUrl.startsWith("/")) || noticeUrl.startsWith("//"))) {
            f.put("noticeUrl", new MessageRef("rewardEvent.noticeUrl.invalid"));
        }
        RewardEventMode mode = enumOf(RewardEventMode.class, body.mode());
        if (mode == null) {
            f.put("mode", new MessageRef("rewardEvent.mode.invalid"));
        }
        RewardEventTrigger trigger = null;
        Integer limit = null;
        if (mode == RewardEventMode.FIRST_COME) {
            trigger = body.firstCome() == null ? null : enumOf(RewardEventTrigger.class, body.firstCome().trigger());
            if (trigger == null) {
                f.put("firstCome.trigger", new MessageRef("rewardEvent.firstCome.trigger.invalid"));
            }
            limit = body.firstCome() == null ? null : body.firstCome().limit();
            if (limit == null || limit < 1 || limit > RewardEventRules.QUOTA_MAX) {
                f.put("firstCome.limit", new MessageRef("rewardEvent.quota.range", String.format("%,d", RewardEventRules.QUOTA_MAX)));
            }
        }
        boolean sourceManual = false;
        boolean sourceComment = false;
        String postRef = null;
        Long postId = null;
        Instant drawFrom = null;
        Instant drawTo = null;
        boolean excludeAuthor = true;
        if (mode == RewardEventMode.SELECTION) {
            RewardSelectionConfig sel = body.selection();
            List<String> sources = sel == null || sel.sources() == null ? List.of() : sel.sources();
            boolean badSource = sources.stream().anyMatch(s -> !"MANUAL".equals(s) && !"COMMENT_DRAW".equals(s));
            sourceManual = sources.contains("MANUAL");
            sourceComment = sources.contains("COMMENT_DRAW");
            if (badSource || !sourceManual && !sourceComment) {
                f.put("selection.sources", new MessageRef("rewardEvent.selection.sources.required"));
            }
            if (sourceComment) {
                RewardCommentDrawConfig draw = sel.commentDraw();
                postRef = draw == null || draw.postRef() == null ? "" : draw.postRef().strip();
                Optional<Long> parsed = RewardEventRules.parsePostRef(postRef);
                if (parsed.isEmpty()) {
                    f.put("selection.commentDraw.postRef", new MessageRef("rewardEvent.postRef.invalid"));
                } else if (members.post(parsed.get()).isEmpty()) {
                    f.put("selection.commentDraw.postRef", new MessageRef("rewardEvent.postRef.notFound"));
                } else {
                    postId = parsed.get();
                }
                drawFrom = draw == null || draw.from() == null ? startAt : draw.from().truncatedTo(ChronoUnit.MICROS);
                drawTo = draw == null || draw.to() == null ? endAt : draw.to().truncatedTo(ChronoUnit.MICROS);
                if (drawFrom != null && drawTo != null && !drawTo.isAfter(drawFrom)) {
                    f.put("selection.commentDraw.to", new MessageRef("rewardEvent.period.order"));
                }
                excludeAuthor = draw == null || draw.excludeAuthor() == null || draw.excludeAuthor();
            }
        }
        RewardBulkScope bulkScope = null;
        Integer minGrade = null;
        java.time.LocalDate joinedFrom = null;
        java.time.LocalDate joinedTo = null;
        boolean identityOnly = false;
        Integer activeDays = null;
        if (mode == RewardEventMode.BULK) {
            RewardBulkConfig bulk = body.bulk();
            bulkScope = bulk == null ? null : enumOf(RewardBulkScope.class, bulk.scope());
            if (bulkScope == null) {
                f.put("bulk.scope", new MessageRef("rewardEvent.bulk.scope.invalid"));
            } else if (bulkScope == RewardBulkScope.CONDITION) {
                minGrade = bulk.minGradeLevel();
                if (minGrade != null && members.grades().stream().noneMatch(g -> RewardMemberQueries.gradeLevel(g) == bulk.minGradeLevel())) {
                    f.put("bulk.minGradeLevel", new MessageRef("rewardEvent.bulk.minGradeLevel.invalid"));
                }
                joinedFrom = bulk.joinedFrom();
                joinedTo = bulk.joinedTo();
                if (joinedFrom != null && joinedTo != null && joinedFrom.isAfter(joinedTo)) {
                    f.put("bulk.joinedTo", new MessageRef("rewardEvent.period.order"));
                }
                identityOnly = Boolean.TRUE.equals(bulk.identityVerifiedOnly());
                activeDays = bulk.activeWithinDays();
                if (activeDays != null && (activeDays < 1 || activeDays > RewardEventRules.ACTIVE_DAYS_MAX)) {
                    f.put("bulk.activeWithinDays", new MessageRef("rewardEvent.bulk.activeWithinDays.range", RewardEventRules.ACTIVE_DAYS_MAX));
                }
            }
        }
        List<TierSpec> tiers = validateTiers(body.tiers(), mode, limit, f);
        if (!f.isEmpty()) {
            throw new ValidationException(f);
        }
        boolean excludeOperators = body.excludeOperators() == null || body.excludeOperators();
        RewardEventEntity.Values values = new RewardEventEntity.Values(name, memo, noticeUrl, mode, startAt, endAt, excludeOperators, trigger, limit, sourceManual,
                sourceComment, sourceComment ? postRef : null, postId, sourceComment ? drawFrom : null, sourceComment ? drawTo : null, excludeAuthor, bulkScope, minGrade,
                joinedFrom, joinedTo, identityOnly, activeDays);
        return new Spec(values, tiers);
    }

    private List<TierSpec> validateTiers(List<RewardTierDto> raw, RewardEventMode mode, Integer firstComeLimit, Map<String, MessageRef> f) {
        List<RewardTierDto> tiers = raw == null ? List.of() : raw;
        if (tiers.isEmpty()) {
            f.put("tiers", new MessageRef("rewardEvent.tiers.required"));
            return List.of();
        }
        if (mode != null && mode != RewardEventMode.SELECTION && tiers.size() != 1) {
            f.put("tiers", new MessageRef("rewardEvent.tiers.single"));
        } else if (tiers.size() > RewardEventRules.TIERS_MAX) {
            f.put("tiers", new MessageRef("rewardEvent.tiers.max", RewardEventRules.TIERS_MAX));
        }
        List<TierSpec> out = new ArrayList<>();
        for (int i = 0; i < tiers.size(); i++) {
            RewardTierDto t = tiers.get(i);
            String p = "tiers[" + i + "]";
            String name = t == null ? "" : RewardEventRules.normalize(t.name());
            if (RewardEventRules.length(name) < 1 || RewardEventRules.length(name) > RewardEventRules.TIER_NAME_MAX) {
                f.put(p + ".name", new MessageRef("rewardEvent.tier.name.length", RewardEventRules.TIER_NAME_MAX));
            }
            int quota = 0;
            if (mode == RewardEventMode.SELECTION) {
                Integer q = t == null ? null : t.quota();
                if (q == null || q < 1 || q > RewardEventRules.QUOTA_MAX) {
                    f.put(p + ".quota", new MessageRef("rewardEvent.quota.range", String.format("%,d", RewardEventRules.QUOTA_MAX)));
                } else {
                    quota = q;
                }
            } else if (mode == RewardEventMode.FIRST_COME) {
                quota = firstComeLimit == null ? 0 : Math.max(0, Math.min(firstComeLimit, RewardEventRules.QUOTA_MAX));
            }
            List<RewardLineDto> lines = t == null || t.rewards() == null ? List.of() : t.rewards();
            if (lines.isEmpty()) {
                f.put(p + ".rewards", new MessageRef("rewardEvent.tier.rewards.required"));
            } else if (lines.size() > RewardEventRules.LINES_MAX) {
                f.put(p + ".rewards", new MessageRef("rewardEvent.tier.rewards.max", RewardEventRules.LINES_MAX));
            }
            List<LineSpec> specs = new ArrayList<>();
            for (int j = 0; j < lines.size(); j++) {
                LineSpec spec = validateLine(lines.get(j), p + ".rewards[" + j + "]", f);
                if (spec != null) {
                    specs.add(spec);
                }
            }
            out.add(new TierSpec(t == null ? null : t.id(), name, quota, specs));
        }
        return out;
    }

    private LineSpec validateLine(RewardLineDto l, String p, Map<String, MessageRef> f) {
        RewardKind kind = l == null ? null : enumOf(RewardKind.class, l.kind());
        if (kind == null) {
            f.put(p + ".kind", new MessageRef("rewardEvent.line.kind.invalid"));
            return null;
        }
        String id = l.id();
        switch (kind) {
            case POINT -> {
                if (l.points() == null || l.points() < 1 || l.points() > RewardEventRules.POINTS_MAX) {
                    f.put(p + ".points", new MessageRef("rewardEvent.line.points.range", String.format("%,d", RewardEventRules.POINTS_MAX)));
                    return null;
                }
                return new LineSpec(id, new RewardEventLineEntity.Values(kind, l.points(), null, null, null, null, null, null, null, null, null, null));
            }
            case SHOP_ITEM -> {
                Optional<ItemRow> item = RewardEventRules.parseId(l.itemId()).flatMap(items::find);
                if (item.isEmpty()) {
                    f.put(p + ".itemId", new MessageRef("rewardEvent.line.itemId.required"));
                    return null;
                }
                if (item.get().kind() == PointShopItemKind.PROFILE_AVATAR) {
                    f.put(p + ".itemId", new MessageRef("rewardEvent.line.itemId.avatar"));
                    return null;
                }
                int quantity = 1;
                if (item.get().usageType() == PointShopUsageType.CONSUMABLE) {
                    if (l.quantity() == null || l.quantity() < 1 || l.quantity() > RewardEventRules.QUANTITY_MAX) {
                        f.put(p + ".quantity", new MessageRef("rewardEvent.line.quantity.range", RewardEventRules.QUANTITY_MAX));
                        return null;
                    }
                    quantity = l.quantity();
                }
                ItemRow it = item.get();
                return new LineSpec(id, new RewardEventLineEntity.Values(kind, null, it.id(), cut(it.name(), 100), it.kind().name(), it.usageType().name(), it.durationDays(),
                        quantity, null, null, null, null));
            }
            case EMOTICON_PACK -> {
                Optional<PackRow> pack = RewardEventRules.parseId(l.packId()).flatMap(packs::find);
                if (pack.isEmpty() || pack.get().status() != EmoticonPackStatus.APPROVED) {
                    f.put(p + ".packId", new MessageRef(pack.isEmpty() ? "rewardEvent.line.packId.required" : "rewardEvent.line.packId.notOnSale"));
                    return null;
                }
                if (pack.get().source() == EmoticonPackSource.CREATOR && pack.get().price() > 0) {
                    f.put(p + ".packId", new MessageRef("rewardEvent.line.packId.creatorPaid"));
                    return null;
                }
                String packName = pack.get().liveRevisionId() == null ? "" : packs.revisions(List.of(pack.get().liveRevisionId())).values().stream().findFirst()
                        .map(EmoticonPackStore.RevisionRow::name).orElse("");
                return new LineSpec(id, new RewardEventLineEntity.Values(kind, null, null, null, null, null, null, null, pack.get().id(), cut(packName, 40), null, null));
            }
            case MANUAL -> {
                String manualName = RewardEventRules.normalize(l.manualName());
                String method = l.manualMethod() == null ? "" : l.manualMethod().strip();
                boolean ok = true;
                if (RewardEventRules.length(manualName) < 1 || RewardEventRules.length(manualName) > RewardEventRules.MANUAL_NAME_MAX) {
                    f.put(p + ".manualName", new MessageRef("rewardEvent.line.manualName.length", RewardEventRules.MANUAL_NAME_MAX));
                    ok = false;
                }
                if (RewardEventRules.length(method) > RewardEventRules.MANUAL_METHOD_MAX) {
                    f.put(p + ".manualMethod", new MessageRef("rewardEvent.line.manualMethod.max", RewardEventRules.MANUAL_METHOD_MAX));
                    ok = false;
                }
                return ok ? new LineSpec(id, new RewardEventLineEntity.Values(kind, null, null, null, null, null, null, null, null, null, manualName, method)) : null;
            }
        }
        return null;
    }

    private static String cut(String text, int max) {
        return text == null ? null : text.length() <= max ? text : text.substring(0, max);
    }

    static <E extends Enum<E>> E enumOf(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        for (E e : type.getEnumConstants()) {
            if (e.name().equals(value)) {
                return e;
            }
        }
        return null;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
