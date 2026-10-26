package com.community.group.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.grade.repository.master.GradeRepositoryQDSL;
import com.community.group.dto.GroupRuleValues;
import com.community.group.dto.request.GroupRuleSaveRequests;
import com.community.group.dto.response.GroupRuleViews;
import com.community.group.entity.GroupCreationMode;
import com.community.group.entity.GroupSettingHistoryEntity;
import com.community.group.entity.GroupSettingSection;
import com.community.group.entity.GroupSettingsEntity;
import com.community.group.entity.GroupStatus;
import com.community.group.entity.GroupVisibility;
import com.community.group.repository.master.GroupRepository;
import com.community.group.repository.master.GroupRepositoryQDSL;
import com.community.group.repository.master.GroupSettingHistoryRepository;
import com.community.group.repository.master.GroupSettingsRepository;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 소모임 설정 네 구획(개설 규칙 · 매니저 · 공개 범위 · 운영 한도) + 변경 이력 — admin-web {@code useGroupSettingsApi.ts} 머리말 그대로
 * (community api-groups.md 4절 「개설 규칙」·「운영 한도」 · 5-1 「매니저 규칙」 · 11-10 「관리자 설정」).
 *
 * <ul>
 *   <li>권한은 컨트롤러가 선언한다(조회 {@code group.read}, 저장 {@code group.settings} — community 는 최고 관리자 전용).</li>
 *   <li>저장은 구획마다 따로, 그 구획의 칸 모두. 틀린 칸은 모아 400 {@code fields.<칸>}(기본 > 최대 인원 {@code fields.limitDefault}, 최소 > 최대 정원 {@code fields.capacityMax}).
 *       설정 행을 잠그고 쓴다(마지막 저장이 이긴다). 서버는 캐시하지 않는다.</li>
 *   <li>값이 실제로 바뀌었을 때만 감사({@code GROUP_*_RULE_UPDATE}, 대상 GROUP_SETTINGS + 구획 — 바뀐 칸만 before/after)와 이력(구획·코드 값) 한 줄을 남긴다.
 *       바뀐 것이 없으면 저장된 구획은 그대로 두고(시각도 오르지 않는다), 기본값으로 돌던 구획은 지금 값으로 저장만 한다.</li>
 *   <li>매니저·공개 범위 구획은 값 저장·검증·이력만이다 — 그 값을 쓰는 기능은 3단계(매니저)·5단계(공개 범위·안내 알림)에서 붙는다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class GroupSettingsAdminService {

    private static final Logger log = LoggerFactory.getLogger(GroupSettingsAdminService.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    static final String TARGET = "GROUP_SETTINGS";
    static final int HISTORY_DEFAULT_SIZE = 20;
    static final int HISTORY_MAX_SIZE = 100;

    /** 숫자 칸 → 표시 단위 문구 키 조각({@code group.setting.unit.<칸>}) */
    private static final List<String> BOOLEAN_FIELDS = List.of("managersEnabled", "visibilityEnabled");

    private final GroupSettingsRepository settingsRows;
    private final GroupSettingsService settings;
    private final GroupSettingHistoryRepository history;
    private final GroupRepository groups;
    private final GroupRepositoryQDSL queries;
    private final GradeRepositoryQDSL grades;
    private final GroupPeople people;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;
    private final Messages messages;

    public GroupSettingsAdminService(GroupSettingsRepository settingsRows, GroupSettingsService settings, GroupSettingHistoryRepository history,
                                     GroupRepository groups, GroupRepositoryQDSL queries, GradeRepositoryQDSL grades,
                                     GroupPeople people, SnowflakeIdGenerator ids, AuditLogService audit, Messages messages) {
        this.settingsRows = settingsRows;
        this.settings = settings;
        this.history = history;
        this.groups = groups;
        this.queries = queries;
        this.grades = grades;
        this.people = people;
        this.ids = ids;
        this.audit = audit;
        this.messages = messages;
    }

    // ── 개설 규칙 ────────────────────────────────────────────────────────────

    public GroupRuleViews.Creation creation() {
        GroupSettingsEntity row = settings.row().orElse(null);
        List<GroupRuleViews.Option> modes = List.of(
                new GroupRuleViews.Option(GroupCreationMode.APPROVAL.name(), messages.get(new MessageRef("group.setting.creationMode.APPROVAL"))),
                new GroupRuleViews.Option(GroupCreationMode.OPEN.name(), messages.get(new MessageRef("group.setting.creationMode.OPEN"))));
        List<GroupRuleViews.Option> gradeOptions = grades.findAllOrdered().stream().map(g -> new GroupRuleViews.Option(g.getGradeKey(), g.getLabel())).toList();
        return new GroupRuleViews.Creation(GroupRuleViews.CreationValues.of(settings.creation(row)), GroupRuleViews.CreationValues.of(settings.creationDefaults()),
                new GroupRuleViews.CreationLimits(modes, gradeOptions, range(GroupSettingsService.MAX_PER_OWNER_MIN, GroupSettingsService.MAX_PER_OWNER_MAX)),
                row == null || row.getCreationUpdatedAt() == null, row == null ? null : row.getCreationUpdatedAt(),
                people.operatorName(row == null ? null : row.getCreationUpdatedBy()), groups.countByStatus(GroupStatus.PENDING));
    }

    @Transactional
    public GroupRuleViews.Creation saveCreation(AdminActor actor, GroupRuleSaveRequests.Creation body) {
        GroupRuleSaveRequests.Creation r = body == null ? new GroupRuleSaveRequests.Creation(null, null, null) : body;
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        GroupCreationMode mode = null;
        if (r.creationMode() instanceof String s) {
            try {
                mode = GroupCreationMode.valueOf(s.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                mode = null;
            }
        }
        if (mode == null) {
            invalid.put("creationMode", new MessageRef("group.setting.creationMode.invalid"));
        }
        String minGrade = r.minGrade() instanceof String s ? s.strip() : null;
        String wantedGrade = minGrade;
        if (minGrade == null || grades.findAllOrdered().stream().noneMatch(g -> g.getGradeKey().equals(wantedGrade))) {
            invalid.put("minGrade", new MessageRef("group.setting.minGrade.invalid"));
        }
        Integer maxPerOwner = intIn(r.maxPerOwner(), GroupSettingsService.MAX_PER_OWNER_MIN, GroupSettingsService.MAX_PER_OWNER_MAX, "maxPerOwner", invalid);
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        GroupSettingsEntity row = lockRow();
        boolean fromDefaults = row.getCreationUpdatedAt() == null;
        GroupRuleValues.Creation before = settings.creation(row);
        GroupRuleValues.Creation after = new GroupRuleValues.Creation(mode, minGrade, maxPerOwner);
        List<String[]> changes = new ArrayList<>();
        diff(changes, "creationMode", before.creationMode().name(), after.creationMode().name());
        diff(changes, "minGrade", before.minGrade(), after.minGrade());
        diff(changes, "maxPerOwner", before.maxPerOwner(), after.maxPerOwner());
        if (changes.isEmpty() && !fromDefaults) {
            return creation();
        }
        Instant now = now();
        row.saveCreation(mode, minGrade, maxPerOwner, actor.operatorId(), now);
        settingsRows.flush();
        recordChange(actor, GroupSettingSection.CREATION, AuditActions.GROUP_CREATION_RULE_UPDATE, fromDefaults, changes, now);
        return creation();
    }

    // ── 매니저 ───────────────────────────────────────────────────────────────

    public GroupRuleViews.Manager manager() {
        GroupSettingsEntity row = settings.row().orElse(null);
        long[] usage = queries.managerUsage();
        List<GroupRuleViews.LimitCount> limitCounts = queries.managerLimitCounts().entrySet().stream()
                .map(e -> new GroupRuleViews.LimitCount(e.getKey(), e.getValue())).toList();
        GroupRuleViews.Range range = range(GroupSettingsService.MANAGER_LIMIT_MIN, GroupSettingsService.MANAGER_LIMIT_MAX);
        return new GroupRuleViews.Manager(settings.manager(row), settings.managerDefaults(), new GroupRuleViews.ManagerLimits(range, range),
                row == null || row.getManagerUpdatedAt() == null, row == null ? null : row.getManagerUpdatedAt(),
                people.operatorName(row == null ? null : row.getManagerUpdatedBy()), new GroupRuleViews.ManagerUsage(usage[0], usage[1], limitCounts));
    }

    @Transactional
    public GroupRuleViews.Manager saveManager(AdminActor actor, GroupRuleSaveRequests.Manager body) {
        GroupRuleSaveRequests.Manager r = body == null ? new GroupRuleSaveRequests.Manager(null, null, null) : body;
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        Boolean enabled = bool(r.managersEnabled(), "managersEnabled", invalid);
        Integer limitDefault = intIn(r.limitDefault(), GroupSettingsService.MANAGER_LIMIT_MIN, GroupSettingsService.MANAGER_LIMIT_MAX, "limitDefault", invalid);
        Integer limitMax = intIn(r.limitMax(), GroupSettingsService.MANAGER_LIMIT_MIN, GroupSettingsService.MANAGER_LIMIT_MAX, "limitMax", invalid);
        if (limitDefault != null && limitMax != null && limitDefault > limitMax) {
            invalid.putIfAbsent("limitDefault", new MessageRef("group.setting.limitDefault.overMax"));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        GroupSettingsEntity row = lockRow();
        boolean fromDefaults = row.getManagerUpdatedAt() == null;
        GroupRuleValues.Manager before = settings.manager(row);
        List<String[]> changes = new ArrayList<>();
        diff(changes, "managersEnabled", before.managersEnabled(), enabled);
        diff(changes, "limitDefault", before.limitDefault(), limitDefault);
        diff(changes, "limitMax", before.limitMax(), limitMax);
        if (changes.isEmpty() && !fromDefaults) {
            return manager();
        }
        Instant now = now();
        row.saveManager(enabled, limitDefault, limitMax, actor.operatorId(), now);
        settingsRows.flush();
        recordChange(actor, GroupSettingSection.MANAGER, AuditActions.GROUP_MANAGER_RULE_UPDATE, fromDefaults, changes, now);
        return manager();
    }

    // ── 공개 범위 ────────────────────────────────────────────────────────────

    public GroupRuleViews.Visibility visibility() {
        return visibility(0);
    }

    private GroupRuleViews.Visibility visibility(int noticeSent) {
        GroupSettingsEntity row = settings.row().orElse(null);
        Map<GroupVisibility, Long> counts = queries.visibilityCounts();
        long noticeTargets = queries.unpublishManagerCount();
        return new GroupRuleViews.Visibility(settings.visibility(row), settings.visibilityDefaults(), new GroupRuleViews.VisibilityLimits(
                range(GroupSettingsService.COOLDOWN_MIN, GroupSettingsService.COOLDOWN_MAX), range(GroupSettingsService.RETRY_MIN, GroupSettingsService.RETRY_MAX),
                range(GroupSettingsService.REJECT_LIMIT_MIN, GroupSettingsService.REJECT_LIMIT_MAX),
                range(GroupSettingsService.DAILY_LIMIT_MIN, GroupSettingsService.DAILY_LIMIT_MAX),
                range(GroupSettingsService.TEXT_MAX_MIN, GroupSettingsService.TEXT_MAX_MAX)),
                row == null || row.getVisibilityUpdatedAt() == null, row == null ? null : row.getVisibilityUpdatedAt(),
                people.operatorName(row == null ? null : row.getVisibilityUpdatedBy()),
                new GroupRuleViews.VisibilityUsage(counts.getOrDefault(GroupVisibility.PUBLIC, 0L), counts.getOrDefault(GroupVisibility.PRIVATE, 0L), 0,
                        row == null ? null : row.getVisibilityNoticeSentAt(), noticeTargets), noticeSent);
    }

    @Transactional
    public GroupRuleViews.Visibility saveVisibility(AdminActor actor, GroupRuleSaveRequests.Visibility body) {
        GroupRuleSaveRequests.Visibility r = body == null ? new GroupRuleSaveRequests.Visibility(null, null, null, null, null, null) : body;
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        Boolean enabled = bool(r.visibilityEnabled(), "visibilityEnabled", invalid);
        Integer cooldown = intIn(r.cooldownDays(), GroupSettingsService.COOLDOWN_MIN, GroupSettingsService.COOLDOWN_MAX, "cooldownDays", invalid);
        Integer retry = intIn(r.retryHours(), GroupSettingsService.RETRY_MIN, GroupSettingsService.RETRY_MAX, "retryHours", invalid);
        Integer reject = intIn(r.rejectLimit(), GroupSettingsService.REJECT_LIMIT_MIN, GroupSettingsService.REJECT_LIMIT_MAX, "rejectLimit", invalid);
        Integer daily = intIn(r.dailyLimit(), GroupSettingsService.DAILY_LIMIT_MIN, GroupSettingsService.DAILY_LIMIT_MAX, "dailyLimit", invalid);
        Integer text = intIn(r.textMax(), GroupSettingsService.TEXT_MAX_MIN, GroupSettingsService.TEXT_MAX_MAX, "textMax", invalid);
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        GroupSettingsEntity row = lockRow();
        boolean fromDefaults = row.getVisibilityUpdatedAt() == null;
        GroupRuleValues.Visibility before = settings.visibility(row);
        List<String[]> changes = new ArrayList<>();
        diff(changes, "visibilityEnabled", before.visibilityEnabled(), enabled);
        diff(changes, "cooldownDays", before.cooldownDays(), cooldown);
        diff(changes, "retryHours", before.retryHours(), retry);
        diff(changes, "rejectLimit", before.rejectLimit(), reject);
        diff(changes, "dailyLimit", before.dailyLimit(), daily);
        diff(changes, "textMax", before.textMax(), text);
        if (changes.isEmpty() && !fromDefaults) {
            return visibility(0);
        }
        Instant now = now();
        row.saveVisibility(enabled, cooldown, retry, reject, daily, text, actor.operatorId(), now);
        settingsRows.flush();
        recordChange(actor, GroupSettingSection.VISIBILITY, AuditActions.GROUP_VISIBILITY_RULE_UPDATE, fromDefaults, changes, now);
        // 켜기 저장 때의 매니저 안내 알림은 공개 범위 기능(5단계)과 함께 붙는다 — 지금은 보내지 않는다(noticeSent 0)
        return visibility(0);
    }

    // ── 운영 한도 ────────────────────────────────────────────────────────────

    public GroupRuleViews.Policy policy() {
        GroupSettingsEntity row = settings.row().orElse(null);
        return new GroupRuleViews.Policy(settings.policy(row), settings.policyDefaults(), new GroupRuleViews.PolicyLimits(
                range(GroupSettingsService.REAPPLY_MIN, GroupSettingsService.REAPPLY_MAX), range(GroupSettingsService.CAPACITY_MIN, GroupSettingsService.CAPACITY_MAX),
                range(GroupSettingsService.CAPACITY_MIN, GroupSettingsService.CAPACITY_MAX),
                range(GroupSettingsService.TRENDING_SIZE_MIN, GroupSettingsService.TRENDING_SIZE_MAX),
                range(GroupSettingsService.TRENDING_DAYS_MIN, GroupSettingsService.TRENDING_DAYS_MAX),
                range(GroupSettingsService.EVENT_UPCOMING_MIN, GroupSettingsService.EVENT_UPCOMING_MAX),
                range(GroupSettingsService.EVENT_AHEAD_MIN, GroupSettingsService.EVENT_AHEAD_MAX),
                range(GroupSettingsService.EVENT_LENGTH_MIN, GroupSettingsService.EVENT_LENGTH_MAX)),
                row == null || row.getPolicyUpdatedAt() == null, row == null ? null : row.getPolicyUpdatedAt(),
                people.operatorName(row == null ? null : row.getPolicyUpdatedBy()));
    }

    @Transactional
    public GroupRuleViews.Policy savePolicy(AdminActor actor, GroupRuleSaveRequests.Policy body) {
        GroupRuleSaveRequests.Policy r = body == null ? new GroupRuleSaveRequests.Policy(null, null, null, null, null, null, null, null) : body;
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        Integer reapply = intIn(r.reapplyDays(), GroupSettingsService.REAPPLY_MIN, GroupSettingsService.REAPPLY_MAX, "reapplyDays", invalid);
        Integer capMin = intIn(r.capacityMin(), GroupSettingsService.CAPACITY_MIN, GroupSettingsService.CAPACITY_MAX, "capacityMin", invalid);
        Integer capMax = intIn(r.capacityMax(), GroupSettingsService.CAPACITY_MIN, GroupSettingsService.CAPACITY_MAX, "capacityMax", invalid);
        Integer trendSize = intIn(r.trendingSize(), GroupSettingsService.TRENDING_SIZE_MIN, GroupSettingsService.TRENDING_SIZE_MAX, "trendingSize", invalid);
        Integer trendDays = intIn(r.trendingActiveDays(), GroupSettingsService.TRENDING_DAYS_MIN, GroupSettingsService.TRENDING_DAYS_MAX, "trendingActiveDays",
                invalid);
        Integer upcoming = intIn(r.eventMaxUpcoming(), GroupSettingsService.EVENT_UPCOMING_MIN, GroupSettingsService.EVENT_UPCOMING_MAX, "eventMaxUpcoming",
                invalid);
        Integer ahead = intIn(r.eventMaxDaysAhead(), GroupSettingsService.EVENT_AHEAD_MIN, GroupSettingsService.EVENT_AHEAD_MAX, "eventMaxDaysAhead", invalid);
        Integer length = intIn(r.eventMaxLengthDays(), GroupSettingsService.EVENT_LENGTH_MIN, GroupSettingsService.EVENT_LENGTH_MAX, "eventMaxLengthDays",
                invalid);
        if (capMin != null && capMax != null && capMin > capMax) {
            invalid.putIfAbsent("capacityMax", new MessageRef("group.setting.capacityMax.belowMin"));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        GroupSettingsEntity row = lockRow();
        boolean fromDefaults = row.getPolicyUpdatedAt() == null;
        GroupRuleValues.Policy b = settings.policy(row);
        List<String[]> changes = new ArrayList<>();
        diff(changes, "reapplyDays", b.reapplyDays(), reapply);
        diff(changes, "capacityMin", b.capacityMin(), capMin);
        diff(changes, "capacityMax", b.capacityMax(), capMax);
        diff(changes, "trendingSize", b.trendingSize(), trendSize);
        diff(changes, "trendingActiveDays", b.trendingActiveDays(), trendDays);
        diff(changes, "eventMaxUpcoming", b.eventMaxUpcoming(), upcoming);
        diff(changes, "eventMaxDaysAhead", b.eventMaxDaysAhead(), ahead);
        diff(changes, "eventMaxLengthDays", b.eventMaxLengthDays(), length);
        if (changes.isEmpty() && !fromDefaults) {
            return policy();
        }
        Instant now = now();
        row.savePolicy(reapply, capMin, capMax, trendSize, trendDays, upcoming, ahead, length, actor.operatorId(), now);
        settingsRows.flush();
        recordChange(actor, GroupSettingSection.POLICY, AuditActions.GROUP_POLICY_RULE_UPDATE, fromDefaults, changes, now);
        return policy();
    }

    // ── 변경 이력 ────────────────────────────────────────────────────────────

    public GroupRuleViews.HistoryPage history(String section, String page, String size) {
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        GroupSettingSection filter = null;
        if (section != null && !section.isBlank()) {
            try {
                filter = GroupSettingSection.valueOf(section.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                invalid.put("section", new MessageRef("group.setting.history.section.invalid"));
            }
        }
        Integer pageNo = intQuery(page, 0, 0, Integer.MAX_VALUE / HISTORY_MAX_SIZE);
        if (pageNo == null) {
            invalid.put("page", new MessageRef("group.list.page.invalid"));
        }
        Integer pageSize = intQuery(size, HISTORY_DEFAULT_SIZE, 1, HISTORY_MAX_SIZE);
        if (pageSize == null) {
            invalid.put("size", new MessageRef("group.list.size.invalid", String.valueOf(HISTORY_MAX_SIZE)));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        long total = queries.settingHistoryCount(filter);
        List<GroupRuleViews.HistoryItem> items = new ArrayList<>();
        if (total > 0 && (long) pageNo * pageSize < total) {
            String deleted = messages.get(new MessageRef("group.person.operatorDeleted"));
            Map<String, String> gradeLabels = new HashMap<>();
            grades.findAllOrdered().forEach(g -> gradeLabels.put(g.getGradeKey(), g.getLabel()));
            for (GroupRepositoryQDSL.HistoryRow row : queries.settingHistory(filter, pageNo, pageSize)) {
                GroupSettingHistoryEntity h = row.history();
                List<GroupRuleViews.HistoryChange> changes = new ArrayList<>();
                for (Map<String, Object> change : parse(h.getChanges())) {
                    String field = String.valueOf(change.get("field"));
                    changes.add(new GroupRuleViews.HistoryChange(field, "group.historyField." + field, display(field, change.get("before"), gradeLabels),
                            display(field, change.get("after"), gradeLabels)));
                }
                items.add(new GroupRuleViews.HistoryItem(Long.toString(h.getId()), h.getAction(), "ADMIN",
                        h.getOperatorId() == null ? null : Long.toString(h.getOperatorId()), row.operatorName() == null ? deleted : row.operatorName(),
                        row.operatorName() == null, h.getChangedAt(), changes, h.getSection().name()));
            }
        }
        return new GroupRuleViews.HistoryPage(items, total, pageNo, pageSize);
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private GroupSettingsEntity lockRow() {
        return settingsRows.findWithLockById(GroupSettingsEntity.ROW_ID).orElseThrow(() -> new IllegalStateException("group_settings 행이 없다(V89)"));
    }

    /** 감사(바뀐 칸만 before/after) + 이력 한 줄 — 바뀐 칸이 없으면 둘 다 남기지 않는다 */
    private void recordChange(AdminActor actor, GroupSettingSection section, String action, boolean fromDefaults, List<String[]> changes, Instant now) {
        if (changes.isEmpty()) {
            log.info("소모임 설정 구획을 지금 값으로 저장했다(기본값에서, 바뀐 값 없음) section={} by operatorId={}", section, actor.operatorId());
            return;
        }
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        List<Map<String, Object>> json = new ArrayList<>();
        for (String[] c : changes) {
            before.put(c[0], c[1]);
            after.put(c[0], c[2]);
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("field", c[0]);
            one.put("before", c[1]);
            one.put("after", c[2]);
            json.add(one);
        }
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), action, AuditResult.SUCCESS).target(TARGET, section.name())
                .with("before", before).with("after", after));
        history.persist(GroupSettingHistoryEntity.create(ids.nextId(), section, fromDefaults ? GroupSettingHistoryEntity.ACTION_CREATE
                : GroupSettingHistoryEntity.ACTION_UPDATE, actor.operatorId(), now, MAPPER.writeValueAsString(json)));
        log.info("소모임 설정을 바꿨다 section={} 칸={}개 by operatorId={}", section, changes.size(), actor.operatorId());
    }

    private static void diff(List<String[]> out, String field, Object before, Object after) {
        String b = before == null ? null : before.toString();
        String a = after == null ? null : after.toString();
        if (!Objects.equals(b, a)) {
            out.add(new String[] {field, b, a});
        }
    }

    /** 코드 값 → 요청 언어의 표시 글자 — 개설 방식 이름 · 등급 이름 · 켜짐/꺼짐 · 「7일」 */
    private String display(String field, Object raw, Map<String, String> gradeLabels) {
        if (raw == null) {
            return null;
        }
        String value = raw.toString();
        if ("creationMode".equals(field)) {
            return messages.get(new MessageRef("group.setting.creationMode." + value));
        }
        if ("minGrade".equals(field)) {
            return gradeLabels.getOrDefault(value, value);
        }
        if (BOOLEAN_FIELDS.contains(field)) {
            return messages.get(new MessageRef("true".equals(value) ? "group.setting.on" : "group.setting.off"));
        }
        return messages.get(new MessageRef("group.setting.unit." + field, value));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(MAPPER.readValue(json, Map[].class)).stream().map(m -> (Map<String, Object>) m).toList();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static GroupRuleViews.Range range(int min, int max) {
        return new GroupRuleViews.Range(min, max);
    }

    /** JSON 정수(문자열·소수·불리언 아님)이고 범위 안 — 아니면 그 칸 위반 */
    private static Integer intIn(Object raw, int min, int max, String field, Map<String, MessageRef> invalid) {
        Integer value = null;
        if (raw instanceof Integer i) {
            value = i;
        } else if (raw instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
            value = l.intValue();
        } else if (raw instanceof Short s) {
            value = (int) s;
        }
        if (value == null || value < min || value > max) {
            invalid.put(field, new MessageRef("group.setting.range", String.valueOf(min), String.valueOf(max)));
            return null;
        }
        return value;
    }

    private static Boolean bool(Object raw, String field, Map<String, MessageRef> invalid) {
        if (raw instanceof Boolean b) {
            return b;
        }
        invalid.put(field, new MessageRef("group.setting.boolean"));
        return null;
    }

    private static Integer intQuery(String raw, int fallback, int min, int max) {
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

    /** DB 가 마이크로초까지 저장하므로 응답과 다음 비교가 같은 값을 보게 자른다 */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
