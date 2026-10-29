package com.community.home.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.home.dto.HomeGadgetSpec;
import com.community.home.dto.HomeGridLayout;
import com.community.home.dto.HomeLayoutItem;
import com.community.home.dto.request.HomeLayoutSaveRequest;
import com.community.home.dto.response.HomeLayoutBodyView;
import com.community.home.dto.response.HomeLayoutGadgetView;
import com.community.home.dto.response.HomeLayoutView;
import com.community.home.entity.HomeGadgetEntity;
import com.community.home.entity.HomeLayoutEntity;
import com.community.home.repository.master.HomeGadgetRepository;
import com.community.home.repository.master.HomeLayoutRepository;
import com.community.notification.service.AdminNotices;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.site.service.SiteThemes;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 관리자 홈 구성(테마마다 배치 한 벌) 조회·저장 — site-theme-layout-contract 「홈 구성」. 저장한 적 없는 테마는 기본 배치({@link HomeLayoutDefaults})를 준다.
 *
 * <ul>
 *   <li>저장은 넣고 뺀 결과 전체다: 빠진 저장 가젯은 지우고, 저장 가젯 ID 는 그대로(제목·노출·꾸미기·설정은 서버 값), 시드 ID({@code seed-<n>})는 그 시드 설정으로,
 *       그 밖의 ID({@code new-<n>})는 종류별 처음 값(테마 기본 꾸미기 · 빈 설정 · PC·모바일 모두 보임)과 보낸 제목으로 새로 만든다.</li>
 *   <li>검사(모아서 {@code 400 VALIDATION}): {@code layout.columns}(12·24) · {@code gadgets}(개수 한도) · {@code gadgets[i].id}(비었거나 겹침) ·
 *       {@code gadgets[i].type}(새 가젯의 모르는 종류) · {@code gadgets[i].title}(새 가젯 1~40자) · {@code layout.items[i]}(없는 가젯·두 번째 자리·범위·종류별 최소 크기) ·
 *       {@code layout.items}(자리 없는 가젯·겹침) · 공구 가젯의 {@code gadgets[i].config.count}(1~10)·{@code gadgets[i].config.sort}(DEADLINE·NEWEST·POPULAR).</li>
 *   <li>공구 가젯({@code GROUP_BUY})은 {@code config}(개수·정렬, 사용자 결정 2026-10-29)를 보내면 그 값으로 바꾼다 — 설정의 다른 칸은 그대로 두고, 보내지 않으면 저장 값 그대로.
 *       다른 종류의 {@code config} 는 무시한다. 응답의 공구 가젯 {@code config} 는 저장 값(없으면 5개 · 마감 임박 순).</li>
 *   <li>통과하면 공유 레이어 {@code UtilHomeLayout.compact} 와 같이 읽는 차례대로 위로 당겨 저장한다. 같은 테마의 저장은 사이트 설정 행 잠금으로 줄 세운다.</li>
 *   <li>저장마다 감사 {@code HOME_LAYOUT_UPDATE}(테마 · 칸 수·가젯 수 전후 · 넣은·뺀 수 · 설정을 바꾼 가젯 수)와 관리자 알림(사이트 설정 변경). 공개 홈({@code GET /api/home/gadgets})은 요청마다 읽는다.</li>
 * </ul>
 */
@Service
public class HomeLayoutAdminService {

    private static final Logger log = LoggerFactory.getLogger(HomeLayoutAdminService.class);
    private static final String GROUP_BUY = "GROUP_BUY";

    private final HomeLayoutRepository layouts;
    private final HomeGadgetRepository gadgets;
    private final SiteSettingRepository settings;
    private final OperatorRepository operators;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;
    private final AdminNotices notices;

    public HomeLayoutAdminService(HomeLayoutRepository layouts, HomeGadgetRepository gadgets, SiteSettingRepository settings, OperatorRepository operators,
                                  SnowflakeIdGenerator ids, AuditLogService audit, AdminNotices notices) {
        this.layouts = layouts;
        this.gadgets = gadgets;
        this.settings = settings;
        this.operators = operators;
        this.ids = ids;
        this.audit = audit;
        this.notices = notices;
    }

    @Transactional(readOnly = true)
    public HomeLayoutView get(String theme) {
        String id = known(theme);
        HomeLayoutEntity row = layouts.findById(id).orElse(null);
        if (row == null) {
            HomeLayoutDefaults.Home home = HomeLayoutDefaults.of(id);
            return new HomeLayoutView(id, home.layout(), views(home.gadgets()), null, null);
        }
        return view(row, gadgets.findByThemeOrderByIdAsc(id));
    }

    public HomeLayoutBodyView defaults(String theme) {
        HomeLayoutDefaults.Home home = HomeLayoutDefaults.of(known(theme));
        return new HomeLayoutBodyView(home.layout(), views(home.gadgets()));
    }

    @Transactional
    public HomeLayoutView save(AdminActor actor, String theme, HomeLayoutSaveRequest request) {
        String themeId = known(theme);
        settings.findWithLockById(SiteSettingEntity.ROW_ID).orElseThrow(() -> ValidationException.of("site", new MessageRef("siteSetting.missing")));
        HomeLayoutEntity row = layouts.findWithLockByTheme(themeId).orElse(null);
        List<HomeGadgetEntity> stored = row == null ? List.of() : gadgets.findByThemeOrderByIdAsc(themeId);
        HomeLayoutDefaults.Home seeds = HomeLayoutDefaults.of(themeId);
        Map<String, HomeGadgetEntity> storedById = new HashMap<>();
        stored.forEach(g -> storedById.put(Long.toString(g.getId()), g));

        // ── 가젯: 남길 것 · 시드에서 만들 것 · 새로 만들 것 ──
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        List<HomeLayoutSaveRequest.Gadget> wanted = request == null || request.gadgets() == null ? List.of() : request.gadgets();
        if (wanted.size() > HomeGadgetKinds.MAX_GADGETS) {
            fields.put("gadgets", new MessageRef("homeLayout.gadgets.max", String.valueOf(HomeGadgetKinds.MAX_GADGETS)));
        }
        Map<String, String> typeOf = new HashMap<>();
        Set<String> seen = new HashSet<>();
        List<Plan> plans = new ArrayList<>();
        for (int i = 0; i < wanted.size(); i++) {
            HomeLayoutSaveRequest.Gadget g = wanted.get(i);
            String path = "gadgets[" + i + "]";
            String id = g == null || g.id() == null ? "" : g.id().strip();
            if (id.isEmpty() || !seen.add(id)) {
                fields.put(path + ".id", new MessageRef("homeLayout.gadget.id"));
                continue;
            }
            HomeGadgetEntity keep = storedById.get(id);
            if (keep != null) {
                typeOf.put(id, keep.getType());
                plans.add(new Plan(id, keep, null, groupBuyConfig(keep.getType(), g.config(), path, fields)));
                continue;
            }
            HomeGadgetSpec seed = id.startsWith(HomeLayoutDefaults.SEED_PREFIX) ? seeds.gadget(id).orElse(null) : null;
            if (seed != null) {
                typeOf.put(id, seed.type());
                plans.add(new Plan(id, null, seed, groupBuyConfig(seed.type(), g.config(), path, fields)));
                continue;
            }
            String type = g.type() == null ? null : g.type().strip();
            String title = g.title() == null ? "" : g.title().strip();
            if (!HomeGadgetKinds.isKnown(type)) {
                fields.put(path + ".type", new MessageRef("homeLayout.gadget.type"));
            }
            if (title.isEmpty() || title.codePointCount(0, title.length()) > HomeGadgetKinds.TITLE_MAX || title.chars().anyMatch(Character::isISOControl)) {
                fields.put(path + ".title", new MessageRef("homeLayout.gadget.title", String.valueOf(HomeGadgetKinds.TITLE_MAX)));
            }
            if (HomeGadgetKinds.isKnown(type)) {
                typeOf.put(id, type);
                plans.add(new Plan(id, null, new HomeGadgetSpec(id, type, title, true, true, HomeGadgetKinds.baseStyle(themeId),
                        HomeLayoutJson.object(), null), groupBuyConfig(type, g.config(), path, fields)));
            }
        }

        // ── 배치: 칸 수 · 자리마다 범위 · 자리 없는 가젯 · 겹침 ──
        HomeLayoutSaveRequest.Layout layout = request == null ? null : request.layout();
        Integer columns = layout == null ? null : layout.columns();
        if (columns == null || !HomeGadgetKinds.COLUMNS.contains(columns)) {
            fields.put("layout.columns", new MessageRef("homeLayout.columns"));
        }
        List<HomeLayoutItem> items = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        List<HomeLayoutSaveRequest.Item> rawItems = layout == null || layout.items() == null ? List.of() : layout.items();
        for (int i = 0; i < rawItems.size(); i++) {
            HomeLayoutSaveRequest.Item it = rawItems.get(i);
            String path = "layout.items[" + i + "]";
            String gadgetId = it == null ? null : it.gadgetId();
            if (gadgetId == null || !typeOf.containsKey(gadgetId) || !placed.add(gadgetId)) {
                fields.put(path, new MessageRef("homeLayout.item.gadget"));
                continue;
            }
            if (columns == null || !HomeGadgetKinds.COLUMNS.contains(columns)) {
                continue;
            }
            String type = typeOf.get(gadgetId);
            if (it.x() == null || it.y() == null || it.w() == null || it.h() == null || it.x() < 0 || it.w() < HomeGadgetKinds.minW(type, columns)
                    || it.x() + it.w() > columns || it.y() < 0 || it.y() > HomeGadgetKinds.MAX_Y || it.h() < HomeGadgetKinds.minH(type)
                    || it.h() > HomeGadgetKinds.MAX_H) {
                fields.put(path, new MessageRef("homeLayout.item.range"));
                continue;
            }
            items.add(new HomeLayoutItem(gadgetId, it.x(), it.y(), it.w(), it.h()));
        }
        if (!fields.containsKey("layout.items") && typeOf.keySet().stream().anyMatch(id -> !placed.contains(id))) {
            fields.put("layout.items", new MessageRef("homeLayout.items.missing"));
        }
        if (!fields.containsKey("layout.items") && overlaps(items)) {
            fields.put("layout.items", new MessageRef("homeLayout.items.overlap"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }

        // ── 저장: 빠진 가젯 지우기 → 새 가젯 넣기 → 배치 ID 바꾸고 위로 당기기 ──
        Instant now = Instant.now();
        Set<String> keptIds = new HashSet<>();
        plans.stream().filter(p -> p.kept() != null).forEach(p -> keptIds.add(p.requestId()));
        List<HomeGadgetEntity> removed = stored.stream().filter(g -> !keptIds.contains(Long.toString(g.getId()))).toList();
        gadgets.deleteAll(removed);
        Map<String, String> rename = new HashMap<>();
        List<HomeGadgetEntity> result = new ArrayList<>();
        int added = 0;
        int configChanged = 0;
        for (Plan p : plans) {
            if (p.kept() != null) {
                JsonNode current = p.kept().toSpec().config();
                if (p.config() != null) {
                    JsonNode next = merged(current, p.config());
                    if (!next.equals(current)) {
                        p.kept().changeConfig(next);
                        configChanged++;
                    }
                }
                result.add(p.kept());
                continue;
            }
            HomeGadgetSpec spec = p.config() == null ? p.spec()
                    : new HomeGadgetSpec(p.spec().id(), p.spec().type(), p.spec().title(), p.spec().desktop(), p.spec().mobile(), p.spec().style(),
                            merged(p.spec().config(), p.config()), p.spec().more());
            HomeGadgetEntity created = HomeGadgetEntity.create(ids.nextId(), themeId, spec, now);
            gadgets.persist(created);
            rename.put(p.requestId(), Long.toString(created.getId()));
            result.add(created);
            added++;
        }
        List<HomeLayoutItem> renamed = items.stream()
                .map(it -> new HomeLayoutItem(rename.getOrDefault(it.gadgetId(), it.gadgetId()), it.x(), it.y(), it.w(), it.h())).toList();
        HomeGridLayout saved = new HomeGridLayout(columns, compact(renamed));
        HomeGridLayout before = row == null ? HomeLayoutDefaults.of(themeId).layout() : row.toLayout();
        if (row == null) {
            row = HomeLayoutEntity.create(themeId, saved, actor.operatorId(), now);
            layouts.persist(row);
        } else {
            row.change(saved, actor.operatorId(), now);
        }

        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.HOME_LAYOUT_UPDATE, AuditResult.SUCCESS).target("SITE", "1")
                .with("theme", themeId)
                .with("before", Map.of("columns", before.columns(), "gadgets", before.items().size()))
                .with("after", Map.of("columns", saved.columns(), "gadgets", saved.items().size()))
                .with("added", added).with("removed", removed.size()).with("configChanged", configChanged));
        notices.siteSettingsChanged(actor, "homeLayout");
        log.info("홈 구성을 저장했다 operatorId={} theme={} gadgets={} added={} removed={}", actor.operatorId(), themeId, result.size(), added, removed.size());
        result.sort(Comparator.comparing(HomeGadgetEntity::getId));
        return view(row, result);
    }

    /** 고를 수 있는 테마인가 — 아니면 404 */
    private static String known(String theme) {
        if (!SiteThemes.isKnown(theme)) {
            throw new CommonException(ErrorCode.NOT_FOUND);
        }
        return theme;
    }

    private HomeLayoutView view(HomeLayoutEntity row, List<HomeGadgetEntity> rows) {
        String updatedBy = row.getUpdatedBy() == null ? null : operators.findById(row.getUpdatedBy()).map(OperatorEntity::getOperatorName).orElse(null);
        return new HomeLayoutView(row.getTheme(), row.toLayout(), views(rows.stream().map(HomeGadgetEntity::toSpec).toList()), row.getUpdatedAt(), updatedBy);
    }

    private static List<HomeLayoutGadgetView> views(List<HomeGadgetSpec> specs) {
        return specs.stream().map(s -> new HomeLayoutGadgetView(s.id(), s.type(), s.title(), s.desktop(), s.mobile(), configView(s))).toList();
    }

    /** 공구 가젯 설정(저장 값, 없으면 처음 값) — 다른 종류는 null */
    private static HomeLayoutGadgetView.Config configView(HomeGadgetSpec s) {
        return GROUP_BUY.equals(s.type()) ? new HomeLayoutGadgetView.Config(HomeGadgetKinds.groupBuyCount(s.config()), HomeGadgetKinds.groupBuySort(s.config())) : null;
    }

    /**
     * 보낸 공구 가젯 설정을 검사한다 — 공구 가젯이 아니거나 보내지 않았으면 null(바꾸지 않음). 틀리면 칸 경로로 모으고 null.
     * @return {@code {count, sort}} 또는 null.
     */
    private static ObjectNode groupBuyConfig(String type, HomeLayoutSaveRequest.Config raw, String path, Map<String, MessageRef> fields) {
        if (!GROUP_BUY.equals(type) || raw == null) {
            return null;
        }
        boolean ok = true;
        if (raw.count() == null || raw.count() < 1 || raw.count() > HomeGadgetKinds.GROUP_BUY_COUNT_MAX) {
            fields.put(path + ".config.count", new MessageRef("homeLayout.gadget.config.count", "1", String.valueOf(HomeGadgetKinds.GROUP_BUY_COUNT_MAX)));
            ok = false;
        }
        if (raw.sort() == null || !HomeGadgetKinds.GROUP_BUY_SORTS.contains(raw.sort())) {
            fields.put(path + ".config.sort", new MessageRef("homeLayout.gadget.config.sort"));
            ok = false;
        }
        return ok ? HomeLayoutJson.object().put("count", raw.count()).put("sort", raw.sort()) : null;
    }

    /** 저장 설정에 바꿀 칸만 덮는다(설정의 다른 칸은 그대로) */
    private static JsonNode merged(JsonNode base, ObjectNode patch) {
        ObjectNode out = base != null && base.isObject() ? (ObjectNode) base.deepCopy() : HomeLayoutJson.object();
        out.setAll(patch);
        return out;
    }

    /** 두 자리가 겹치는가(사각형) */
    private static boolean overlaps(List<HomeLayoutItem> items) {
        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                HomeLayoutItem a = items.get(i);
                HomeLayoutItem b = items.get(j);
                if (a.x() < b.x() + b.w() && b.x() < a.x() + a.w() && a.y() < b.y() + b.h() && b.y() < a.y() + a.h()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 읽는 차례((y, x), 같으면 ID)대로 놓으며 위로 당긴다 — 공유 레이어 {@code UtilHomeLayout.compact} 와 같은 계산 */
    private static List<HomeLayoutItem> compact(List<HomeLayoutItem> items) {
        List<HomeLayoutItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingInt(HomeLayoutItem::y).thenComparingInt(HomeLayoutItem::x).thenComparing(HomeLayoutItem::gadgetId));
        List<HomeLayoutItem> placed = new ArrayList<>();
        for (HomeLayoutItem it : sorted) {
            int y = 0;
            for (HomeLayoutItem p : placed) {
                if (p.x() < it.x() + it.w() && it.x() < p.x() + p.w()) {
                    y = Math.max(y, p.y() + p.h());
                }
            }
            placed.add(new HomeLayoutItem(it.gadgetId(), it.x(), y, it.w(), it.h()));
        }
        return placed;
    }

    /** 저장할 가젯 하나 — 남길 저장 가젯 또는 새로 만들 값, 바꿀 공구 가젯 설정(없으면 null) */
    private record Plan(String requestId, HomeGadgetEntity kept, HomeGadgetSpec spec, ObjectNode config) {
    }
}
