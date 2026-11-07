package com.community.rewardevent.service;

import com.community.audit.entity.QAuditLogEntity;
import com.community.operator.service.OperatorMemberAccountService;
import com.community.point.service.PointPolicy;
import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.repository.master.PointShopItemStore;
import com.community.rewardevent.dto.RewardMemberRef;
import com.community.rewardevent.dto.response.RewardDrawView;
import com.community.rewardevent.dto.response.RewardEventAuditView;
import com.community.rewardevent.dto.response.RewardEventCatalogView;
import com.community.rewardevent.dto.response.RewardEventDetailView;
import com.community.rewardevent.dto.response.RewardEventPageView;
import com.community.rewardevent.dto.response.RewardGrantPreviewView;
import com.community.rewardevent.dto.response.RewardRecipientPageView;
import com.community.rewardevent.dto.response.RewardRunView;
import com.community.rewardevent.entity.RewardBulkScope;
import com.community.rewardevent.entity.RewardEventDrawEntity;
import com.community.rewardevent.entity.RewardEventEntity;
import com.community.rewardevent.entity.RewardEventMode;
import com.community.rewardevent.entity.RewardEventStatus;
import com.community.rewardevent.entity.RewardKind;
import com.community.rewardevent.entity.RewardRecipientStatus;
import com.community.rewardevent.exception.RewardEventNotFoundException;
import com.community.rewardevent.repository.master.RewardEventStore;
import com.community.rewardevent.repository.master.RewardEventStore.EventFilter;
import com.community.rewardevent.repository.master.RewardEventStore.LineRow;
import com.community.rewardevent.repository.master.RewardEventStore.TierRow;
import com.community.rewardevent.repository.master.RewardMemberQueries;
import com.community.rewardevent.repository.master.RewardMemberQueries.BulkCondition;
import com.community.rewardevent.repository.master.RewardMemberQueries.MemberRow;
import com.community.rewardevent.repository.master.RewardRecipientStore;
import com.community.rewardevent.repository.master.RewardRecipientStore.RecipientFilter;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 보상 이벤트 읽기(event-admin-contract 「API」) — 목록·상세·보상 고르기용·회원 찾기·받는 사람·추첨 기록·지급 내역·지급 미리 보기·기록(감사를 이 이벤트로 거른 것).
 * 이메일은 부르는 쪽(관리자 서버)이 넘기는 가리개({@code member.pii} 가 없으면 가린다)를 거친다. 나뉜 읽기는 한 스냅숏({@code REPEATABLE READ}).
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class RewardEventQueryService {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final QAuditLogEntity A = QAuditLogEntity.auditLogEntity;
    private static final int AUDIT_LIMIT = 500;
    private static final String ACTION_PREFIX = "REWARD_EVENT_";

    private final RewardEventStore events;
    private final RewardRecipientStore recipients;
    private final RewardMemberQueries members;
    private final RewardEventViews views;
    private final PointShopItemStore items;
    private final PointPolicy policy;
    private final OperatorMemberAccountService operatorAccounts;
    private final JPAQueryFactory query;
    private final Messages messages;
    private final Clock clock;

    public RewardEventQueryService(RewardEventStore events, RewardRecipientStore recipients, RewardMemberQueries members, RewardEventViews views, PointShopItemStore items,
                                   PointPolicy policy, OperatorMemberAccountService operatorAccounts,
                                   @org.springframework.beans.factory.annotation.Qualifier(com.community.zconfiguration.jpa.JpaMasterConfig.QUERY_FACTORY) JPAQueryFactory query,
                                   Messages messages, ObjectProvider<Clock> clocks) {
        this.events = events;
        this.recipients = recipients;
        this.members = members;
        this.views = views;
        this.items = items;
        this.policy = policy;
        this.operatorAccounts = operatorAccounts;
        this.query = query;
        this.messages = messages;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 목록 한 쪽 — 번호 내림차순, 탭 건수는 검색어·방식을 적용한 상태별 건수 */
    public RewardEventPageView list(String status, String mode, String q, Integer page, Integer size) {
        Map<String, MessageRef> f = new LinkedHashMap<>();
        RewardEventStatus st = status == null || status.isBlank() ? null : RewardEventEditService.enumOf(RewardEventStatus.class, status);
        if (status != null && !status.isBlank() && st == null) {
            f.put("status", new MessageRef("rewardEvent.status.invalid"));
        }
        RewardEventMode md = mode == null || mode.isBlank() ? null : RewardEventEditService.enumOf(RewardEventMode.class, mode);
        if (mode != null && !mode.isBlank() && md == null) {
            f.put("mode", new MessageRef("rewardEvent.mode.invalid"));
        }
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;
        pageCheck(p, s, f);
        String text = q == null || q.isBlank() ? null : q.strip();
        Instant now = now();
        EventFilter filter = new EventFilter(st, md, text, text == null ? null : RewardEventRules.parseId(text).orElse(null), now);
        long total = events.count(filter);
        List<RewardEventEntity> rows = total == 0 ? List.of() : events.page(filter, p, s);
        Map<String, Long> counts = new LinkedHashMap<>();
        events.counts(new EventFilter(null, md, text, filter.no(), now)).forEach((k, v) -> counts.put(k.name(), v));
        return new RewardEventPageView(views.rows(rows, now), total, counts);
    }

    public RewardEventDetailView detail(long id) {
        return views.detail(events.event(id).orElseThrow(RewardEventNotFoundException::new), now());
    }

    /** 보상 고르기용 — 품목(아바타 제외 · 판매 중단 포함) · 판매 중 팩(운영·무료만 고를 수 있음) · 등급 · 고액 문턱 */
    public RewardEventCatalogView catalog() {
        List<RewardEventCatalogView.Item> itemList = items.list(null, PointShopItemKind.PROFILE_AVATAR).stream()
                .map(i -> new RewardEventCatalogView.Item(Long.toString(i.id()), i.name(), i.kind().name(), i.usageType().name(), i.durationDays(), i.active())).toList();
        List<RewardEventCatalogView.Pack> packs = members.approvedPacks().stream()
                .map(p -> new RewardEventCatalogView.Pack(Long.toString(p.id()), p.name() == null ? "" : p.name(), p.source(), p.price(),
                        !("CREATOR".equals(p.source()) && p.price() > 0))).toList();
        List<RewardEventCatalogView.Grade> grades = members.grades().stream()
                .map(g -> new RewardEventCatalogView.Grade(RewardMemberQueries.gradeLevel(g), RewardMemberQueries.gradeLabel(g))).toList();
        return new RewardEventCatalogView(itemList, packs, grades, policy.lots().adjustLargeThreshold());
    }

    /** 회원 찾기(닉네임·이메일·회원 번호 일부 — 20명) */
    public List<RewardMemberRef> searchMembers(String q, UnaryOperator<String> emailView) {
        String text = q == null ? "" : q.strip();
        if (text.isEmpty()) {
            return List.of();
        }
        return members.search(text, RewardEventRules.MEMBER_SEARCH_LIMIT).stream().map(m -> RewardEventViews.member(m.id(), m, emailView)).toList();
    }

    /** 받는 사람 한 쪽 — 선정 시각 내림차순 */
    public RewardRecipientPageView recipients(long id, String status, String tierId, String q, Integer page, Integer size, UnaryOperator<String> emailView) {
        events.event(id).orElseThrow(RewardEventNotFoundException::new);
        Map<String, MessageRef> f = new LinkedHashMap<>();
        RewardRecipientStatus st = status == null || status.isBlank() ? null : RewardEventEditService.enumOf(RewardRecipientStatus.class, status);
        if (status != null && !status.isBlank() && st == null) {
            f.put("status", new MessageRef("rewardEvent.status.invalid"));
        }
        Long tier = tierId == null || tierId.isBlank() ? null : RewardEventRules.parseId(tierId).orElse(-1L);
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;
        pageCheck(p, s, f);
        String text = q == null || q.isBlank() ? null : q.strip();
        RecipientFilter filter = new RecipientFilter(id, st, tier, text, text == null ? null : RewardEventRules.parseId(text).orElse(null));
        long total = recipients.count(filter);
        return new RewardRecipientPageView(total == 0 ? List.of() : views.recipients(id, recipients.page(filter, p, s), emailView), total);
    }

    /** 추첨 기록 — 최근 것이 위 */
    public List<RewardDrawView> draws(long id, UnaryOperator<String> emailView) {
        events.event(id).orElseThrow(RewardEventNotFoundException::new);
        List<RewardEventDrawEntity> list = events.draws(id);
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, String> tierNames = new LinkedHashMap<>();
        events.tiers(id).forEach(t -> tierNames.put(t.id(), t.name()));
        Map<Long, String> titles = members.postTitles(list.stream().map(RewardEventDrawEntity::getPostId).toList());
        Map<Long, String> names = members.operatorNames(list.stream().map(RewardEventDrawEntity::getOperatorId).filter(Objects::nonNull).toList());
        Set<Long> userIds = new HashSet<>();
        list.forEach(d -> userIds.addAll(Arrays.asList(d.getPickedUserIds())));
        Map<Long, MemberRow> people = members.members(userIds);
        return list.stream().map(d -> new RewardDrawView(Long.toString(d.getId()), d.getCreatedAt(), RewardEventViews.operator(d.getOperatorId(), names),
                Long.toString(d.getPostId()), titles.get(d.getPostId()), Long.toString(d.getTierId()), tierNames.get(d.getTierId()), d.getCandidateCount(), d.getPicked(),
                RewardEventRules.seedText(d.getSeed()), Arrays.stream(d.getPickedUserIds()).map(u -> RewardEventViews.member(u, people.get(u), emailView)).toList())).toList();
    }

    /** 지급 내역 — 최근 것이 위 */
    public List<RewardRunView> runs(long id) {
        events.event(id).orElseThrow(RewardEventNotFoundException::new);
        return views.runs(events.runs(id));
    }

    /**
     * 지급 미리 보기 — 일괄은 조건으로 지금 계산한 대상, 그 밖은 대기 중인 받는 사람. {@code largeRequired} = 1인 포인트 합(대상이 있는 등수 중 가장 큰 것)이 고액 문턱을 넘거나 일괄에
     * 포인트 보상이 있다.
     */
    public RewardGrantPreviewView grantPreview(long id) {
        RewardEventEntity e = events.event(id).orElseThrow(RewardEventNotFoundException::new);
        return preview(e, now());
    }

    public RewardGrantPreviewView preview(RewardEventEntity e, Instant now) {
        List<TierRow> tiers = events.tiers(e.getId());
        List<LineRow> lines = events.lines(e.getId());
        Map<Long, Long> counts;
        if (e.getMode() == RewardEventMode.BULK) {
            counts = Map.of(tiers.get(0).id(), members.countBulkTargets(condition(e), excludedOperators(e.isExcludeOperators()), now));
        } else {
            counts = recipients.countByTier(e.getId(), RewardRecipientStatus.PENDING);
        }
        List<RewardGrantPreviewView.Tier> out = new ArrayList<>();
        long maxPoints = 0;
        long totalPoints = 0;
        long itemCount = 0;
        long manualCount = 0;
        long targets = 0;
        List<Long> usedPoints = new ArrayList<>();
        List<Long> allPoints = new ArrayList<>();
        for (TierRow t : tiers) {
            long count = counts.getOrDefault(t.id(), 0L);
            List<LineRow> tl = lines.stream().filter(l -> l.tierId() == t.id()).toList();
            long per = RewardEventRules.pointsOf(tl);
            allPoints.add(per);
            long perItems = tl.stream().mapToLong(LineRow::itemCount).sum();
            long perManual = tl.stream().filter(l -> l.kind() == RewardKind.MANUAL).count();
            out.add(new RewardGrantPreviewView.Tier(Long.toString(t.id()), t.name(), count, per * count, perItems * count, perManual * count));
            if (count > 0) {
                maxPoints = Math.max(maxPoints, per);
                usedPoints.add(per);
                targets += count;
                totalPoints += per * count;
                itemCount += perItems * count;
                manualCount += perManual * count;
            }
        }
        int threshold = policy.lots().adjustLargeThreshold();
        return new RewardGrantPreviewView(targets, maxPoints, totalPoints, itemCount, manualCount, out, threshold,
                // 일괄은 대상 수와 상관없이 포인트 보상이 있으면 고액(지급 때 대상이 정해진다)
                RewardEventRules.largeRequired(e.getMode(), e.getMode() == RewardEventMode.BULK ? allPoints : usedPoints, threshold));
    }

    /** 일괄 조건 */
    public static BulkCondition condition(RewardEventEntity e) {
        return new BulkCondition(e.getBulkScope() != RewardBulkScope.CONDITION, e.getBulkMinGrade(), e.getBulkJoinedFrom(), e.getBulkJoinedTo(), e.isBulkIdentityOnly(),
                e.getBulkActiveDays());
    }

    /** 빼는 운영자 회원 계정(운영자 수만큼) */
    public Set<Long> excludedOperators(boolean exclude) {
        return exclude ? operatorAccounts.allActiveOperatorUserIds() : Set.of();
    }

    /** 기록 — 감사 기록을 이 이벤트(대상 REWARD_EVENT + 번호)로 거른 것, 최근 것이 위(500줄까지). 한 줄 설명은 요청 언어 */
    public List<RewardEventAuditView> audit(long id) {
        events.event(id).orElseThrow(RewardEventNotFoundException::new);
        List<Tuple> rows = query.select(A.id, A.occurredAt, A.actorType, A.actorId, A.action, A.detail).from(A)
                .where(A.targetType.eq(RewardEventSupport.TARGET), A.targetId.eq(Long.toString(id)), A.action.startsWith(ACTION_PREFIX))
                .orderBy(A.occurredAt.desc(), A.id.desc()).limit(AUDIT_LIMIT).fetch();
        Map<Long, String> names = members.operatorNames(rows.stream().filter(t -> "OPERATOR".equals(t.get(A.actorType))).map(t -> t.get(A.actorId))
                .filter(Objects::nonNull).toList());
        Locale locale = messages.locale();
        return rows.stream().map(t -> {
            String action = t.get(A.action).substring(ACTION_PREFIX.length());
            Long actor = "OPERATOR".equals(t.get(A.actorType)) ? t.get(A.actorId) : null;
            return new RewardEventAuditView(Long.toString(t.get(A.id)), t.get(A.occurredAt), RewardEventViews.operator(actor, names), action,
                    auditLine(action, t.get(A.detail), locale));
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private String auditLine(String action, String json, Locale locale) {
        Map<String, Object> d = Map.of();
        if (json != null) {
            try {
                d = MAPPER.readValue(json, Map.class);
            } catch (RuntimeException e) {
                d = Map.of();
            }
        }
        String key = "rewardEvent.audit." + action;
        Object[] args = switch (action) {
            case "CREATE" -> new Object[] {s(d.get("name"))};
            case "AUTO_CLOSE" -> {
                key = key + "." + ("FULL".equals(d.get("cause")) ? "FULL" : "PERIOD");
                yield new Object[0];
            }
            case "DRAW" -> new Object[] {s(d.get("candidateCount")), s(d.get("picked")), s(d.get("seed"))};
            case "RECIPIENT_ADD" -> new Object[] {s(d.get("count"))};
            case "RECIPIENT_REMOVE" -> new Object[] {s(d.get("userId"))};
            case "GRANT", "RETRY" -> {
                if ("DONE".equals(d.get("phase"))) {
                    key = key + ".DONE";
                    yield new Object[] {s(d.get("success")), s(d.get("failed")), s(d.get("skipped")), number(d.get("points"))};
                }
                key = key + ".START";
                yield new Object[] {s(d.get("targetCount"))};
            }
            case "REVOKE" -> new Object[] {s(d.get("count")), number(d.get("points")), s(d.get("items")), s(d.get("reason"))};
            case "DELIVERED" -> new Object[] {s(d.get("memo"))};
            case "CANCEL" -> new Object[] {s(d.get("reason"))};
            default -> new Object[0];
        };
        String text = messages.find(new MessageRef(key, args));
        return text == null ? action : text;
    }

    private static String s(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String number(Object v) {
        return v instanceof Number n ? String.format("%,d", n.longValue()) : s(v);
    }

    private static void pageCheck(int page, int size, Map<String, MessageRef> f) {
        if (page < 0) {
            f.put("page", new MessageRef("rewardEvent.page.invalid"));
        }
        if (size < 1 || size > RewardEventRules.PAGE_SIZE_MAX) {
            f.put("size", new MessageRef("rewardEvent.size.range", RewardEventRules.PAGE_SIZE_MAX));
        }
        if (!f.isEmpty()) {
            throw new ValidationException(f);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
