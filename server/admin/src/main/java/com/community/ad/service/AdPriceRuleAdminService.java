package com.community.ad.service;

import com.community.ad.dto.AdPlacementHistory;
import com.community.ad.dto.AdPriceRuleAdmin;
import com.community.ad.repository.master.AdPriceRuleHistoryRepositoryQDSL;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.bizapplication.entity.BizPriceRuleHistoryEntity;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.exception.BizApplicationException;
import com.community.bizapplication.repository.master.BizPriceRuleHistoryRepository;
import com.community.bizapplication.repository.master.BizPriceRuleStore;
import com.community.bizapplication.repository.master.BizPriceRuleStore.PriceRule;
import com.community.bizapplication.repository.master.BizPriceRuleStore.Schedule;
import com.community.bizapplication.service.BizSlotService;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import com.community.zconfiguration.time.ServiceTime;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 상품 가격(요금표 예약 변경 — admin-web {@code /ads/placements?tab=prices}, docs/features/ad-price-rules-contract.md). 조회 {@code ad.read} · 쓰기 {@code ad.settings}(권한은 컨트롤러).
 *
 * <ul>
 *   <li><b>지금 값</b>은 가격 기간표에서 적용 시각이 지난 가장 늦은 줄({@link BizPriceRuleStore#effectiveAt}) — 신청 검사·견적과 같은 값. 배치 없음.
 *       {@code schedules} 는 아직 적용 전({@code effectiveAt > 지금})인 줄만, 적용 시각 차례. {@code recentChanges} 는 적용 시각이 지난 기간표 줄(직전 값과 다른 칸)과
 *       받기 켬/끔 이력을 합쳐 최근 차례로 상품마다 {@value #RECENT_LIMIT}건.</li>
 *   <li><b>예약 만들기·고치기</b>(규칙 3·4): 적용 시각은 지금보다 뒤 · 같은 상품에 같은 시각 하나 — 400 {@code fields.effectiveAt}. 칸 범위는 DB CHECK 와 같다 — 400
 *       {@code fields.unitPrice|capacity|maxDays|maxLeadDays}. 고치기·취소는 아직 적용 전인 예약만(아니면 404 {@code AD_PRICE_SCHEDULE_NOT_FOUND}).</li>
 *   <li><b>여러 상품</b>(규칙 5): 고른 상품마다 예약 1건, 단가만 바꾸고 나머지 칸은 그 시각 직전 값. 기준 = 각 상품의 지금 단가, 비율 −90~300% 또는 정액, 10원 단위 버림, 0원 아래면
 *       400 {@code fields.adjust.value} — 하나라도 안 되면 아무것도 저장하지 않는다(한 트랜잭션).</li>
 *   <li><b>신청 받기</b>(규칙 6): 바로 바꾼다(긴급 스위치).</li>
 *   <li><b>같은 상품 동시 편집</b>은 요금표 줄 잠금({@link BizPriceRuleStore#lockRules})으로 한 번에 하나씩. 같은 시각 중복은 유일 제약이 한 번 더 막는다.</li>
 *   <li><b>기록</b>: 감사 {@code AD_PRICE_SCHEDULE_CREATE|UPDATE|CANCEL}·{@code AD_PRICE_ACCEPTING_UPDATE}(대상 {@code AD_PRICE_RULES} · 상품, 여러 상품 {@code bulk} · 모두 취소
 *       {@code all} 한 건) + 변경 이력(V208 {@code biz_price_rule_history}, 칸 코드 {@code schedules.<상품>.effectiveAt}·{@code schedules.<상품>.<칸>}·{@code rules.<상품>.<칸>}).
 *       바뀐 것이 없는 저장은 기록도 없다.</li>
 * </ul>
 */
@Service
public class AdPriceRuleAdminService {

    static final String AUDIT_TARGET = "AD_PRICE_RULES";
    static final int RECENT_LIMIT = 3;
    static final int PERCENT_MIN = -90;
    static final int PERCENT_MAX = 300;
    static final int HISTORY_MAX_SIZE = 100;
    static final int HISTORY_DEFAULT_SIZE = 20;
    /** 결과 단가를 이 단위로 버린다(규칙 5) */
    static final long ROUND_UNIT = 10;
    /** 화면 차례 — admin-web 목업과 같다(광고 자리 6 · 핫딜 글 · 핫딜 노출 2) */
    static final List<BizProduct> ORDER = List.of(BizProduct.WIDE_BANNER, BizProduct.STRIP_BANNER, BizProduct.SQUARE_BANNER, BizProduct.SMALL_BANNER, BizProduct.WING_BANNER,
            BizProduct.TEXT_LINE, BizProduct.HOTDEAL_POST, BizProduct.HOTDEAL_TOP, BizProduct.HOTDEAL_CATEGORY);
    private static final String LABEL_KEY_PREFIX = "settingHistory.field.";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** 예약 값 넷 */
    record Values(long unitPrice, Integer capacity, int maxDays, int maxLeadDays) {

        static Values of(PriceRule r) {
            return new Values(r.unitPrice(), r.capacity(), r.maxDays(), r.maxLeadDays());
        }

        static Values of(Schedule s) {
            return new Values(s.unitPrice(), s.capacity(), s.maxDays(), s.maxLeadDays());
        }

        Map<String, Object> map() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("unitPrice", unitPrice);
            out.put("capacity", capacity);
            out.put("maxDays", maxDays);
            out.put("maxLeadDays", maxLeadDays);
            return out;
        }
    }

    private final BizPriceRuleStore rules;
    private final BizSlotService slots;
    private final BizPriceRuleHistoryRepository history;
    private final AdPriceRuleHistoryRepositoryQDSL historyQueries;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;
    private final Messages messages;
    private final Clock clock;

    public AdPriceRuleAdminService(BizPriceRuleStore rules, BizSlotService slots, BizPriceRuleHistoryRepository history, AdPriceRuleHistoryRepositoryQDSL historyQueries,
                                   AuditLogService audit, SnowflakeIdGenerator ids, Messages messages, ObjectProvider<Clock> clocks) {
        this.rules = rules;
        this.slots = slots;
        this.history = history;
        this.historyQueries = historyQueries;
        this.audit = audit;
        this.ids = ids;
        this.messages = messages;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 조회 ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdPriceRuleAdmin.View get() {
        Instant now = now();
        Map<BizProduct, PriceRule> rows = rules.rows();
        Map<BizProduct, PriceRule> current = rules.effectiveAt(now);
        Map<BizProduct, List<Schedule>> timelines = new EnumMap<>(BizProduct.class);
        Set<Long> editors = new HashSet<>();
        for (BizProduct product : ORDER) {
            List<Schedule> timeline = rules.schedulesOf(product);
            timelines.put(product, timeline);
            timeline.forEach(s -> editors.add(s.effectiveAt().isAfter(now) ? s.createdBy() : s.lastEditor()));
        }
        Map<Long, String> names = historyQueries.operatorNames(editors);
        String deleted = messages.get(new MessageRef("ad.placement.history.operatorDeleted"));
        LocalDate today = ServiceTime.today(clock);
        List<AdPriceRuleAdmin.Rule> out = new ArrayList<>();
        Instant updatedAt = null;
        AdPriceRuleAdmin.Operator updatedBy = null;
        for (BizProduct product : ORDER) {
            PriceRule rule = current.get(product);
            if (rule == null) {
                continue;
            }
            List<Schedule> timeline = timelines.get(product);
            List<AdPriceRuleAdmin.Schedule> schedules = timeline.stream().filter(s -> s.effectiveAt().isAfter(now))
                    .map(s -> new AdPriceRuleAdmin.Schedule(Long.toString(s.id()), s.effectiveAt(), s.unitPrice(), s.capacity(), s.maxDays(), s.maxLeadDays(),
                            operator(s.createdBy(), names, deleted), s.createdAt(), s.updatedAt()))
                    .toList();
            List<AdPriceRuleAdmin.RecentChange> recent = new ArrayList<>();
            for (Schedule s : timeline) {
                if (s.effectiveAt().isAfter(now)) {
                    continue;
                }
                List<AdPriceRuleAdmin.RecentChangeField> fields = appliedFields(rows.get(product), timeline, s);
                if (!fields.isEmpty()) {
                    recent.add(new AdPriceRuleAdmin.RecentChange(Long.toString(s.id()), s.effectiveAt(), operator(s.lastEditor(), names, deleted).name(), fields));
                }
                if (updatedAt == null || s.effectiveAt().isAfter(updatedAt)) {
                    updatedAt = s.effectiveAt();
                    updatedBy = operator(s.lastEditor(), names, deleted);
                }
            }
            historyQueries.recent(product, RECENT_LIMIT).forEach(row -> recent.add(recentChange(product, row, deleted)));
            recent.sort(Comparator.comparing(AdPriceRuleAdmin.RecentChange::changedAt).reversed());
            out.add(new AdPriceRuleAdmin.Rule(product.name(), rule.perPost() ? "POST" : "DAY", rule.unitPrice(), rule.capacity(), rule.maxDays(), rule.maxLeadDays(),
                    rule.accepting(), occupancy(product, rule, today), schedules, recent.stream().limit(RECENT_LIMIT).toList()));
        }
        var latest = historyQueries.latestRuleChange().orElse(null);
        if (latest != null && (updatedAt == null || latest.history().getChangedAt().isAfter(updatedAt))) {
            updatedAt = latest.history().getChangedAt();
            updatedBy = operator(latest, deleted);
        }
        return new AdPriceRuleAdmin.View(out, updatedAt, updatedBy);
    }

    /** 적용된 기간표 줄 하나가 바꾼 칸 — 그 시각 직전 값과 다른 칸만(건당 상품의 한도는 늘 null) */
    private static List<AdPriceRuleAdmin.RecentChangeField> appliedFields(PriceRule row, List<Schedule> timeline, Schedule s) {
        Map<String, Object> before = valuesBefore(row, timeline, s.effectiveAt(), s.id()).map();
        Map<String, Object> after = Values.of(row.with(s)).map();
        List<AdPriceRuleAdmin.RecentChangeField> out = new ArrayList<>();
        for (String field : before.keySet()) {
            if (!Objects.equals(before.get(field), after.get(field))) {
                out.add(new AdPriceRuleAdmin.RecentChangeField(field, text(before.get(field)), text(after.get(field))));
            }
        }
        return out;
    }

    private static AdPriceRuleAdmin.Operator operator(long id, Map<Long, String> names, String deleted) {
        return new AdPriceRuleAdmin.Operator(Long.toString(id), names.getOrDefault(id, deleted));
    }

    /** 오늘(한국 날짜)부터 시작일 선택 범위 끝까지 날짜별 최대 동시 겹침의 분포 — 겹침 큰 차례, 0 은 뺀다. 건당 상품은 빈 목록 */
    private List<AdPriceRuleAdmin.Occupancy> occupancy(BizProduct product, PriceRule rule, LocalDate today) {
        if (rule.perPost()) {
            return List.of();
        }
        Map<Integer, Integer> days = new TreeMap<>((a, b) -> Integer.compare(b, a));
        for (int peak : slots.dailyPeaks(product, today, today.plusDays(rule.maxLeadDays()))) {
            if (peak > 0) {
                days.merge(peak, 1, Integer::sum);
            }
        }
        return days.entrySet().stream().map(e -> new AdPriceRuleAdmin.Occupancy(e.getKey(), e.getValue())).toList();
    }

    /** 받기 켬/끔 이력 한 줄 → 최근 변경 */
    private static AdPriceRuleAdmin.RecentChange recentChange(BizProduct product, AdPriceRuleHistoryRepositoryQDSL.Row row, String deleted) {
        String prefix = "rules." + product.name() + ".";
        List<AdPriceRuleAdmin.RecentChangeField> fields = parse(row.history().getChanges()).stream().filter(c -> c.field() != null && c.field().startsWith(prefix))
                .map(c -> new AdPriceRuleAdmin.RecentChangeField(c.field().substring(prefix.length()), c.before(), c.after())).toList();
        return new AdPriceRuleAdmin.RecentChange(row.history().getId().toString(), row.history().getChangedAt(), operator(row, deleted).name(), fields);
    }

    private static AdPriceRuleAdmin.Operator operator(AdPriceRuleHistoryRepositoryQDSL.Row row, String deleted) {
        return new AdPriceRuleAdmin.Operator(row.history().getOperatorId().toString(), row.operatorName() == null ? deleted : row.operatorName());
    }

    // ── 예약 ───────────────────────────────────────────────────────────────

    @Transactional
    public AdPriceRuleAdmin.View createSchedule(AdminActor actor, String productText, AdPriceRuleAdmin.ScheduleRequest body) {
        BizProduct product = product(productText);
        Instant now = now();
        rules.lockRules(List.of(product));
        PriceRule row = rules.rows().get(product);
        List<Schedule> all = schedulesOf(product);
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        Instant at = effectiveAt(body == null ? null : body.effectiveAt(), now, fields);
        Values after = values(row, body, fields);
        if (at != null && all.stream().anyMatch(s -> s.effectiveAt().equals(at))) {
            fields.put("effectiveAt", new MessageRef("ad.price.effectiveAt.duplicate"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        Values before = valuesBefore(row, all, at, null);
        long id = ids.nextId();
        rules.insertSchedule(id, product, at, after.unitPrice(), after.capacity(), after.maxDays(), after.maxLeadDays(), actor.operatorId(), now);
        List<AdPlacementHistory.Change> changes = new ArrayList<>();
        scheduleChanges(changes, product, before, after, null, at);
        record(actor, BizPriceRuleHistoryEntity.ACTION_CREATE, null, now, changes);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("effectiveAt", at.toString());
        detail.put("schedules", List.of(scheduleDetail(product, id, before, after)));
        audit(actor, AuditActions.AD_PRICE_SCHEDULE_CREATE, product.name(), detail);
        return get();
    }

    @Transactional
    public AdPriceRuleAdmin.View updateSchedule(AdminActor actor, String productText, String idText, AdPriceRuleAdmin.ScheduleRequest body) {
        BizProduct product = product(productText);
        Instant now = now();
        rules.lockRules(List.of(product));
        Schedule old = pendingSchedule(product, idText, now);
        PriceRule row = rules.rows().get(product);
        List<Schedule> all = schedulesOf(product);
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        Instant at = effectiveAt(body == null ? null : body.effectiveAt(), now, fields);
        Values after = values(row, body, fields);
        if (at != null && all.stream().anyMatch(s -> s.id() != old.id() && s.effectiveAt().equals(at))) {
            fields.put("effectiveAt", new MessageRef("ad.price.effectiveAt.duplicate"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        Values before = Values.of(old);
        if (before.equals(after) && old.effectiveAt().equals(at)) {
            return get(); // 바뀐 것이 없다 — 기록 없음
        }
        rules.updateSchedule(old.id(), at, after.unitPrice(), after.capacity(), after.maxDays(), after.maxLeadDays(), actor.operatorId(), now);
        List<AdPlacementHistory.Change> changes = new ArrayList<>();
        scheduleChanges(changes, product, before, after, old.effectiveAt(), at);
        record(actor, BizPriceRuleHistoryEntity.ACTION_UPDATE, null, now, changes);
        Map<String, Object> from = before.map();
        from.put("effectiveAt", old.effectiveAt().toString());
        Map<String, Object> to = after.map();
        to.put("effectiveAt", at.toString());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", Long.toString(old.id()));
        detail.put("before", from);
        detail.put("after", to);
        audit(actor, AuditActions.AD_PRICE_SCHEDULE_UPDATE, product.name(), detail);
        return get();
    }

    @Transactional
    public AdPriceRuleAdmin.View cancelSchedule(AdminActor actor, String productText, String idText) {
        BizProduct product = product(productText);
        Instant now = now();
        rules.lockRules(List.of(product));
        Schedule old = pendingSchedule(product, idText, now);
        cancel(actor, List.of(old), product.name(), now);
        return get();
    }

    /** 걸린(아직 적용 전) 예약 모두 취소 — 없으면 아무 기록도 없다 */
    @Transactional
    public AdPriceRuleAdmin.View cancelAll(AdminActor actor) {
        Instant now = now();
        rules.lockRules(List.of(BizProduct.values()));
        List<Schedule> pending = rules.pendingSchedules(now);
        if (!pending.isEmpty()) {
            cancel(actor, pending, "all", now);
        }
        return get();
    }

    private void cancel(AdminActor actor, List<Schedule> gone, String target, Instant now) {
        rules.deleteSchedules(gone.stream().map(Schedule::id).toList());
        List<AdPlacementHistory.Change> changes = new ArrayList<>();
        List<Map<String, Object>> details = new ArrayList<>();
        for (Schedule s : gone) {
            changes.add(new AdPlacementHistory.Change("schedules." + s.product().name() + ".effectiveAt", s.effectiveAt().toString(), null));
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("id", Long.toString(s.id()));
            d.put("product", s.product().name());
            d.put("effectiveAt", s.effectiveAt().toString());
            d.putAll(Values.of(s).map());
            details.add(d);
        }
        record(actor, BizPriceRuleHistoryEntity.ACTION_DELETE, null, now, changes);
        audit(actor, AuditActions.AD_PRICE_SCHEDULE_CANCEL, target, Map.of("schedules", details));
    }

    /** 여러 상품 한꺼번에 — 고른 상품마다 예약 1건(단가만). 전부 되거나 전부 안 된다 */
    @Transactional
    public AdPriceRuleAdmin.View bulk(AdminActor actor, AdPriceRuleAdmin.BulkRequest body) {
        Instant now = now();
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        Set<BizProduct> products = new LinkedHashSet<>();
        if (body == null || body.products() == null || body.products().isEmpty()) {
            fields.put("products", new MessageRef("ad.price.products.required"));
        } else {
            for (String name : body.products()) {
                BizProduct p = parseProduct(name);
                if (p == null) {
                    fields.put("products", new MessageRef("ad.price.products.unknown"));
                    break;
                }
                products.add(p);
            }
        }
        AdPriceRuleAdmin.Adjust adjust = body == null ? null : body.adjust();
        String kind = adjust == null || adjust.kind() == null ? null : adjust.kind().strip();
        Integer value = adjust == null ? null : exactInt(adjust.value());
        if (adjust == null || adjust.value() == null || value == null || value == 0) {
            fields.put("adjust.value", new MessageRef("ad.price.adjust.required"));
        } else if (!"PERCENT".equals(kind) && !"AMOUNT".equals(kind)) {
            fields.put("adjust.kind", new MessageRef("ad.price.adjust.kind"));
        } else if ("PERCENT".equals(kind) && (value < PERCENT_MIN || value > PERCENT_MAX)) {
            fields.put("adjust.value", new MessageRef("ad.price.adjust.percent", String.valueOf(PERCENT_MIN), String.valueOf(PERCENT_MAX)));
        }
        Instant at = effectiveAt(body == null ? null : body.effectiveAt(), now, fields);
        if (!fields.containsKey("products")) {
            rules.lockRules(products);
        }
        Map<BizProduct, PriceRule> rows = rules.rows();
        Map<BizProduct, PriceRule> current = rules.effectiveAt(now);
        Map<BizProduct, Long> prices = new LinkedHashMap<>();
        if (!fields.containsKey("products")) {
            if (at != null && products.stream().anyMatch(p -> schedulesOf(p).stream().anyMatch(s -> s.effectiveAt().equals(at)))) {
                fields.put("effectiveAt", new MessageRef("ad.price.effectiveAt.duplicateBulk"));
            }
            if (!fields.containsKey("adjust.value") && !fields.containsKey("adjust.kind")) {
                for (BizProduct p : products) {
                    Long next = adjusted(current.get(p).unitPrice(), kind, value);
                    if (next == null) {
                        fields.put("adjust.value", new MessageRef("ad.price.adjust.negative"));
                        break;
                    }
                    prices.put(p, next);
                }
            }
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        List<AdPlacementHistory.Change> changes = new ArrayList<>();
        List<Map<String, Object>> details = new ArrayList<>();
        for (BizProduct p : ORDER) {
            if (!products.contains(p)) {
                continue;
            }
            PriceRule row = rows.get(p);
            Values before = valuesBefore(row, schedulesOf(p), at, null);
            Values after = new Values(prices.get(p), before.capacity(), before.maxDays(), before.maxLeadDays());
            long id = ids.nextId();
            rules.insertSchedule(id, p, at, after.unitPrice(), after.capacity(), after.maxDays(), after.maxLeadDays(), actor.operatorId(), now);
            scheduleChanges(changes, p, before, after, null, at);
            details.add(scheduleDetail(p, id, before, after));
        }
        record(actor, BizPriceRuleHistoryEntity.ACTION_CREATE, null, now, changes);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("effectiveAt", at.toString());
        detail.put("adjust", Map.of("kind", kind, "value", value));
        detail.put("schedules", details);
        audit(actor, AuditActions.AD_PRICE_SCHEDULE_CREATE, "bulk", detail);
        return get();
    }

    /**
     * 조정한 단가 — 기준 × (100 + 비율) / 100 또는 기준 + 정액, 10원 단위 버림. 0 아래면 null(규칙 5). 정수 계산이라 소수 오차가 없다.
     */
    static Long adjusted(long base, String kind, int value) {
        long raw;
        if ("PERCENT".equals(kind)) {
            long scaled = base * (100L + value); // 원 × 100
            if (scaled < 0) {
                return null;
            }
            raw = scaled / 100; // 버림(0 이상)
        } else {
            raw = base + value;
            if (raw < 0) {
                return null;
            }
        }
        return raw / ROUND_UNIT * ROUND_UNIT;
    }

    // ── 신청 받기 ──────────────────────────────────────────────────────────

    @Transactional
    public AdPriceRuleAdmin.View setAccepting(AdminActor actor, String productText, AdPriceRuleAdmin.AcceptingRequest body) {
        BizProduct product = product(productText);
        if (body == null || body.accepting() == null) {
            throw ValidationException.of("accepting", new MessageRef("ad.price.accepting.required"));
        }
        Instant now = now();
        rules.lockRules(List.of(product));
        boolean before = rules.rows().get(product).accepting();
        boolean after = body.accepting();
        if (before == after) {
            return get();
        }
        rules.setAccepting(product, after, actor.operatorId(), now);
        record(actor, BizPriceRuleHistoryEntity.ACTION_UPDATE, product, now,
                List.of(new AdPlacementHistory.Change("rules." + product.name() + ".accepting", String.valueOf(before), String.valueOf(after))));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", Map.of("accepting", before));
        detail.put("after", Map.of("accepting", after));
        audit(actor, AuditActions.AD_PRICE_ACCEPTING_UPDATE, product.name(), detail);
        return get();
    }

    // ── 이력 ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdPlacementHistory.Page history(String page, String size) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        MessageRef range = new MessageRef("ad.placement.history.page.range", String.valueOf(HISTORY_MAX_SIZE));
        Integer pageNumber = pageInt(page, 0, 0, Integer.MAX_VALUE / HISTORY_MAX_SIZE);
        if (pageNumber == null) {
            fields.put("page", range);
        }
        Integer pageSize = pageInt(size, HISTORY_DEFAULT_SIZE, 1, HISTORY_MAX_SIZE);
        if (pageSize == null) {
            fields.put("size", range);
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        long total = historyQueries.count();
        List<AdPlacementHistory.Item> items = new ArrayList<>();
        if (total > 0 && (long) pageNumber * pageSize < total) {
            String deleted = messages.get(new MessageRef("ad.placement.history.operatorDeleted"));
            for (AdPriceRuleHistoryRepositoryQDSL.Row row : historyQueries.page(pageNumber, pageSize)) {
                BizPriceRuleHistoryEntity e = row.history();
                boolean gone = row.operatorName() == null;
                List<AdPlacementHistory.ChangeView> changes = parse(e.getChanges()).stream()
                        .map(c -> new AdPlacementHistory.ChangeView(c.field(), LABEL_KEY_PREFIX + c.field(), c.before(), c.after())).toList();
                items.add(new AdPlacementHistory.Item(e.getId().toString(), e.getAction(), e.getSource(), e.getOperatorId().toString(),
                        gone ? deleted : row.operatorName(), gone, e.getChangedAt(), changes));
            }
        }
        return new AdPlacementHistory.Page(items, total, pageNumber, pageSize);
    }

    // ── 도우미 ─────────────────────────────────────────────────────────────

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS); // DB(timestamptz) 정밀도
    }

    private static BizProduct parseProduct(String name) {
        if (name == null) {
            return null;
        }
        try {
            return BizProduct.valueOf(name.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 경로의 상품 — 모르면 404 */
    private static BizProduct product(String name) {
        BizProduct p = parseProduct(name);
        if (p == null) {
            throw BizApplicationException.priceProductNotFound();
        }
        return p;
    }

    /** 그 상품의 아직 적용 전인 예약 — 없는 ID · 다른 상품 · 적용 시각이 지난 예약은 404 */
    private Schedule pendingSchedule(BizProduct product, String idText, Instant now) {
        long id;
        try {
            id = Long.parseLong(idText == null ? "" : idText.strip());
        } catch (NumberFormatException e) {
            throw BizApplicationException.priceScheduleNotFound();
        }
        return rules.schedule(id).filter(s -> s.product() == product && s.effectiveAt().isAfter(now)).orElseThrow(BizApplicationException::priceScheduleNotFound);
    }

    private List<Schedule> schedulesOf(BizProduct product) {
        return rules.schedulesOf(product);
    }

    /** 그 시각 바로 앞의 값 — 그 시각보다 앞선 가장 늦은 기간표 줄(빼라는 것 빼고), 없으면 요금표 줄 값 */
    static Values valuesBefore(PriceRule row, List<Schedule> schedules, Instant at, Long excludeId) {
        PriceRule out = row;
        for (Schedule s : schedules) {
            if (Objects.equals(excludeId, s.id()) || !s.effectiveAt().isBefore(at)) {
                continue;
            }
            out = out.with(s);
        }
        return Values.of(out);
    }

    /** 적용 시각 — 비었거나 읽을 수 없으면 required, 지금보다 뒤가 아니면 past */
    private static Instant effectiveAt(String text, Instant now, Map<String, MessageRef> fields) {
        Instant at = null;
        if (text != null && !text.isBlank()) {
            try {
                at = OffsetDateTime.parse(text.strip()).toInstant();
            } catch (DateTimeParseException e) {
                at = null;
            }
        }
        if (at == null) {
            fields.put("effectiveAt", new MessageRef("ad.price.effectiveAt.required"));
            return null;
        }
        at = at.truncatedTo(ChronoUnit.MICROS);
        if (!at.isAfter(now)) {
            fields.put("effectiveAt", new MessageRef("ad.price.effectiveAt.past"));
            return null;
        }
        return at;
    }

    /** 예약 값 넷 검사(DB CHECK 와 같은 범위) — 틀린 칸은 {@code fields} 에 더하고 null 이 섞인 값을 돌려준다(그때는 쓰지 않는다) */
    private static Values values(PriceRule row, AdPriceRuleAdmin.ScheduleRequest body, Map<String, MessageRef> fields) {
        Long unitPrice = body == null ? null : exactLong(body.unitPrice());
        if (unitPrice == null || unitPrice < 0) {
            fields.put("unitPrice", new MessageRef("ad.price.unitPrice"));
        }
        Integer capacity = null;
        if (row.perPost()) {
            if (body != null && body.capacity() != null) {
                fields.put("capacity", new MessageRef("ad.price.capacity.post"));
            }
        } else {
            capacity = body == null ? null : exactInt(body.capacity());
            if (capacity == null || capacity < 1) {
                fields.put("capacity", new MessageRef("ad.price.capacity"));
            }
        }
        Integer maxDays = body == null ? null : exactInt(body.maxDays());
        if (maxDays == null || maxDays < 1) {
            fields.put("maxDays", new MessageRef("ad.price.maxDays"));
        }
        Integer maxLeadDays = body == null ? null : exactInt(body.maxLeadDays());
        if (maxLeadDays == null || maxLeadDays < 1) {
            fields.put("maxLeadDays", new MessageRef("ad.price.maxLeadDays"));
        }
        if (unitPrice == null || maxDays == null || maxLeadDays == null) {
            return null;
        }
        return new Values(unitPrice, capacity, maxDays, maxLeadDays);
    }

    private static Long exactLong(BigDecimal v) {
        if (v == null) {
            return null;
        }
        try {
            return v.longValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private static Integer exactInt(BigDecimal v) {
        if (v == null) {
            return null;
        }
        try {
            return v.intValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /** 예약 이력 칸 — 적용 시각(바뀌었으면) + 직전 값과 다른 칸 */
    private static void scheduleChanges(List<AdPlacementHistory.Change> out, BizProduct product, Values before, Values after, Instant oldAt, Instant newAt) {
        String prefix = "schedules." + product.name() + ".";
        if (!Objects.equals(oldAt, newAt)) {
            out.add(new AdPlacementHistory.Change(prefix + "effectiveAt", oldAt == null ? null : oldAt.toString(), newAt == null ? null : newAt.toString()));
        }
        Map<String, Object> a = before.map();
        Map<String, Object> b = after.map();
        for (String field : a.keySet()) {
            if (!Objects.equals(a.get(field), b.get(field))) {
                out.add(new AdPlacementHistory.Change(prefix + field, text(a.get(field)), text(b.get(field))));
            }
        }
    }

    private static Map<String, Object> scheduleDetail(BizProduct product, long id, Values before, Values after) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("product", product.name());
        d.put("id", Long.toString(id));
        d.put("before", before.map());
        d.put("after", after.map());
        return d;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void record(AdminActor actor, String action, BizProduct product, Instant now, List<AdPlacementHistory.Change> changes) {
        if (changes.isEmpty()) {
            return;
        }
        history.persist(BizPriceRuleHistoryEntity.admin(ids.nextId(), action, actor.operatorId(), product, now, MAPPER.writeValueAsString(changes)));
    }

    private void audit(AdminActor actor, String action, String targetId, Map<String, Object> detail) {
        audit.record(new AuditEvent(AuditActorType.OPERATOR, actor.operatorId(), action, AuditResult.SUCCESS, AUDIT_TARGET, targetId, actor.ip(), detail));
    }

    private static List<AdPlacementHistory.Change> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(MAPPER.readValue(json, AdPlacementHistory.Change[].class));
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static Integer pageInt(String raw, int fallback, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.strip());
            return value < min || value > max ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
