package com.community.cache.service;

import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.dto.AuditActions;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.cache.definition.CacheCatalog;
import com.community.cache.definition.CacheDefinition;
import com.community.cache.definition.CacheNames;
import com.community.cache.definition.CacheSampleKind;
import com.community.cache.definition.CacheServerRole;
import com.community.cache.definition.CacheUsageKind;
import com.community.cache.dto.response.CacheAdminLinkView;
import com.community.cache.dto.response.CacheDetailView;
import com.community.cache.dto.response.CacheExclusionView;
import com.community.cache.dto.response.CacheGadgetPlacementView;
import com.community.cache.dto.response.CacheNodeStateView;
import com.community.cache.dto.response.CacheNodeSummaryView;
import com.community.cache.dto.response.CacheOverviewView;
import com.community.cache.dto.response.CachePreviewPageView;
import com.community.cache.dto.response.CacheRefreshLogView;
import com.community.cache.dto.response.CacheRefreshResultView;
import com.community.cache.dto.response.CacheSettingChangeView;
import com.community.cache.dto.response.CacheUsageSampleView;
import com.community.cache.dto.response.CacheUsageView;
import com.community.cache.dto.response.CacheView;
import com.community.cache.entity.CacheGlobalSettingEntity;
import com.community.cache.entity.CacheRefreshLogEntity;
import com.community.cache.entity.CacheSettingChangeEntity;
import com.community.cache.entity.CacheSettingEntity;
import com.community.cache.exception.CacheDisabledException;
import com.community.cache.exception.CacheGlobalDisabledException;
import com.community.cache.exception.CacheNotFoundException;
import com.community.cache.repository.master.CacheGlobalSettingRepository;
import com.community.cache.repository.master.CacheSampleStore;
import com.community.cache.repository.master.CacheSettingChangeRepository;
import com.community.cache.repository.master.CacheSettingRepository;
import com.community.cache.repository.master.CacheStateStore;
import com.community.cache.repository.master.CacheStateStore.NodeStateRow;
import com.community.cache.repository.master.CacheStateStore.SettingRow;
import com.community.home.dto.GadgetSpec;
import com.community.home.entity.GadgetPageEntity;
import com.community.home.entity.GadgetEntity;
import com.community.home.repository.master.BoardLayoutRepository;
import com.community.home.repository.master.GadgetPageLayoutRepository;
import com.community.home.repository.master.GadgetPageRepository;
import com.community.home.repository.master.GadgetRepository;
import com.community.home.repository.master.HomeLayoutRepository;
import com.community.home.service.BoardLayoutDefaults;
import com.community.home.service.GadgetPageInitial;
import com.community.home.service.GadgetKinds;
import com.community.home.service.HomeLayoutDefaults;
import com.community.hotdeal.repository.master.AdminDealQueries;
import com.community.hotdeal.service.DealSettingsReader;
import com.community.permission.dto.AdminActor;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.site.service.SiteThemes;
import com.community.zconfiguration.cache.CacheProperties;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캐시 관리(docs/features/cache-admin-contract.md) — 목록·상세(서버별 보고를 합친다)와 지금 갱신·전체 갱신·TTL 변경·켜기/끄기.
 *
 * <ul>
 *   <li>서버별 값은 각 서버가 DB 에 보고한 것({@code cache_node_states})이다 — {@code irobit.cache.node-timeout-seconds}(기본 60초) 넘게 보고가 없는 서버는 뺀다.
 *       목록 줄은 그 캐시를 두는 서버들의 합(항목 수·적중·누락)이고 마지막 갱신은 가장 늦게 비운 서버 기준(한 대라도 띄운 뒤 안 비웠으면 null)이다.</li>
 *   <li>갱신 대기 = 반영 세대가 지금 세대보다 작거나 반영 켜짐이 실제 켜짐과 다른 서버가 있음. 대기가 시작된 시각은 마지막 갱신 요청·켜기/끄기 시각.</li>
 *   <li>지금 갱신은 세대 번호를 올리고(202) 각 서버가 몇 초 안에 비운다. 꺼진 캐시는 409 {@code CACHE_DISABLED}, 전체 갱신은 꺼진 캐시를 건너뛴다. 확인 창은 화면 몫(세션 정리 결정 1).</li>
 *   <li>TTL 은 분류별 허용 범위 안에서만(400 {@code fields.ttlSeconds}), 바꿔도 비우지 않는다(세션 정리 결정 3). 꺼진 캐시도 바꿀 수 있다.</li>
 *   <li>캐시별 켜기/끄기는 전체가 켜져 있을 때만(409 {@code CACHE_GLOBAL_DISABLED}). 저장 값은 전체를 꺼도 남는다.</li>
 *   <li>바꾸는 일은 모두 감사 기록과 설정 변경 이력(TTL·켜기/끄기)·갱신 기록(수동)을 남긴다.</li>
 * </ul>
 */
@Service
public class CacheAdminService {

    static final int LOG_LIMIT = 20;
    private static final String TARGET = "CACHE";
    private static final String QUICK_LINKS = "QUICK_LINKS";
    private static final String NOTICE_TEXT = "NOTICE_TEXT";
    private static final List<String> UNCACHED = List.of(QUICK_LINKS, NOTICE_TEXT, GadgetKinds.AD, GadgetKinds.AD_TEXT_LINE,
            GadgetKinds.BOARD_BODY);
    private static final List<String> LAYOUT_CACHES = List.of(CacheNames.HOME_LAYOUT, CacheNames.PAGES_LAYOUT, CacheNames.BOARDS_LAYOUT);

    private final CacheStateStore store;
    private final CacheSettingRepository settings;
    private final CacheGlobalSettingRepository globals;
    private final CacheSettingChangeRepository changes;
    private final CacheRefresher refresher;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;
    private final Messages messages;
    private final CacheProperties properties;
    private final SiteSettingRepository siteSettings;
    private final BoardCatalog catalog;
    private final GadgetPageRepository pages;
    private final GadgetPageLayoutRepository pageLayouts;
    private final HomeLayoutRepository homeLayouts;
    private final BoardLayoutRepository boardLayouts;
    private final GadgetRepository gadgets;
    private final AdminDealQueries deals;
    private final DealSettingsReader dealSettings;
    private final CacheSampleStore samples;

    public CacheAdminService(CacheStateStore store, CacheSettingRepository settings, CacheGlobalSettingRepository globals, CacheSettingChangeRepository changes,
                             CacheRefresher refresher, SnowflakeIdGenerator ids, AuditLogService audit, Messages messages, CacheProperties properties,
                             SiteSettingRepository siteSettings, BoardCatalog catalog, GadgetPageRepository pages, GadgetPageLayoutRepository pageLayouts,
                             HomeLayoutRepository homeLayouts, BoardLayoutRepository boardLayouts, GadgetRepository gadgets, AdminDealQueries deals,
                             DealSettingsReader dealSettings, CacheSampleStore samples) {
        this.store = store;
        this.settings = settings;
        this.globals = globals;
        this.changes = changes;
        this.refresher = refresher;
        this.ids = ids;
        this.audit = audit;
        this.messages = messages;
        this.properties = properties;
        this.siteSettings = siteSettings;
        this.catalog = catalog;
        this.pages = pages;
        this.pageLayouts = pageLayouts;
        this.homeLayouts = homeLayouts;
        this.boardLayouts = boardLayouts;
        this.gadgets = gadgets;
        this.deals = deals;
        this.dealSettings = dealSettings;
        this.samples = samples;
    }

    // ── 읽기 ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CacheOverviewView overview() {
        Instant now = Instant.now();
        State state = state(now);
        List<CacheView> views = CacheCatalog.all().stream().map(d -> view(d, state)).toList();
        Map<String, Instant> checked = new TreeMap<>();
        Map<String, String> roles = new HashMap<>();
        for (NodeStateRow r : state.nodes()) {
            roles.put(r.nodeId(), r.role());
            if (r.lastCheckedAt() != null) {
                checked.merge(r.nodeId(), r.lastCheckedAt(), (a, b) -> a.isAfter(b) ? a : b);
            } else {
                checked.putIfAbsent(r.nodeId(), null);
            }
        }
        List<CacheNodeSummaryView> nodes = new ArrayList<>();
        // 차례: API → ADMIN, 이름 차례
        roles.keySet().stream().sorted(Comparator.comparing((String id) -> CacheServerRole.valueOf(roles.get(id)).ordinal()).thenComparing(Function.identity()))
                .forEach(id -> nodes.add(new CacheNodeSummaryView(id, roles.get(id), checked.get(id))));
        return new CacheOverviewView(nodes, properties.poll(), views, state.globalEnabled(), exclusions(), previewPages(state), state.publicUrl());
    }

    @Transactional(readOnly = true)
    public CacheDetailView detail(String cacheKey) {
        CacheDefinition def = definition(cacheKey);
        State state = state(Instant.now());
        CacheView view = view(def, state);
        List<CacheNodeStateView> nodes = nodesOf(def, state).stream()
                .map(r -> new CacheNodeStateView(r.nodeId(), r.role(), r.appliedGeneration(), r.appliedEnabled(), r.lastClearedAt(), r.lastCheckedAt(), r.entryCount(),
                        r.hitCount(), r.missCount()))
                .toList();
        List<CacheRefreshLogView> refreshes = store.refreshLogs(def.key(), LOG_LIMIT).stream()
                .map(r -> new CacheRefreshLogView(Long.toString(r.id()), r.requestedAt(), r.triggerKind(), r.allCaches(), source(r.sourceCode(), r.sourceDetail()),
                        r.operatorName(), r.generation()))
                .toList();
        List<CacheSettingChangeView> settingChanges = store.settingChanges(def.key(), LOG_LIMIT).stream()
                .map(r -> new CacheSettingChangeView(Long.toString(r.id()), r.changedAt(), r.kind(), r.fromSeconds(), r.toSeconds(), r.enabled(), r.operatorName()))
                .toList();
        List<CacheGadgetPlacementView> placements = def.gadgetKind() == null ? List.of() : state.placements().of(def.gadgetKind());
        Integer distinct = def.gadgetKind() == null ? null : state.placements().distinctConfigs(def.gadgetKind());
        return new CacheDetailView(view, nodes, refreshes, settingChanges, placements, distinct);
    }

    // ── 바꾸기 ───────────────────────────────────────────────────────────────

    /** 지금 갱신(한 캐시) — 꺼진 캐시는 409 */
    @Transactional
    public CacheRefreshResultView refresh(AdminActor actor, String cacheKey) {
        CacheDefinition def = definition(cacheKey);
        Instant now = Instant.now();
        if (!effective(def.key())) {
            throw new CacheDisabledException();
        }
        Map<String, Long> bumped = refresher.bump(List.of(def.key()), CacheRefreshLogEntity.MANUAL, false, null, null, actor.operatorId(), now);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.CACHE_REFRESH, AuditResult.SUCCESS).target(TARGET, def.key())
                .with("generation", bumped.get(def.key())));
        State state = state(now);
        return new CacheRefreshResultView(now, List.of(view(def, state)));
    }

    /** 전체 갱신 — 켜진 캐시만(꺼진 캐시는 비울 항목이 없어 건너뛴다) */
    @Transactional
    public CacheRefreshResultView refreshAll(AdminActor actor) {
        Instant now = Instant.now();
        State before = state(now);
        List<String> keys = CacheCatalog.all().stream().map(CacheDefinition::key).filter(key -> before.effective(key)).toList();
        refresher.bump(keys, CacheRefreshLogEntity.MANUAL, true, null, null, actor.operatorId(), now);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.CACHE_REFRESH_ALL, AuditResult.SUCCESS).target(TARGET, null)
                .with("count", keys.size()));
        State state = state(now);
        return new CacheRefreshResultView(now, CacheCatalog.all().stream().map(d -> view(d, state)).toList());
    }

    /** TTL 변경 — 분류별 허용 범위 밖이면 400 {@code fields.ttlSeconds}. 비우지 않는다 */
    @Transactional
    public CacheView updateTtl(AdminActor actor, String cacheKey, int ttlSeconds) {
        CacheDefinition def = definition(cacheKey);
        if (!def.kind().allows(ttlSeconds)) {
            throw new ValidationException(Map.of("ttlSeconds", new MessageRef("cache.ttl.range", def.kind().minTtlSeconds(), def.kind().maxTtlSeconds())));
        }
        Instant now = Instant.now();
        CacheSettingEntity row = lockedRow(def.key(), now);
        int from = row.getTtlSeconds() == null ? def.defaultTtlSeconds() : row.getTtlSeconds();
        if (from != ttlSeconds || row.getTtlSeconds() == null) {
            row.changeTtl(ttlSeconds, actor.operatorId(), now);
            if (from != ttlSeconds) {
                changes.persist(CacheSettingChangeEntity.ttl(ids.nextId(), def.key(), from, ttlSeconds, actor.operatorId(), now));
                audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.CACHE_TTL_CHANGE, AuditResult.SUCCESS).target(TARGET, def.key())
                        .with("from", from).with("to", ttlSeconds));
            }
        }
        return view(def, state(now));
    }

    /** 이 캐시 켜기/끄기 — 전체가 꺼져 있으면 409 */
    @Transactional
    public CacheView setEnabled(AdminActor actor, String cacheKey, boolean enabled) {
        CacheDefinition def = definition(cacheKey);
        Instant now = Instant.now();
        CacheGlobalSettingEntity global = lockedGlobal(now);
        if (!global.isEnabled()) {
            throw new CacheGlobalDisabledException();
        }
        CacheSettingEntity row = lockedRow(def.key(), now);
        if (row.isEnabled() != enabled) {
            boolean was = row.isEnabled();
            row.changeEnabled(enabled, actor.operatorId(), now);
            changes.persist(CacheSettingChangeEntity.enabled(ids.nextId(), def.key(), enabled, actor.operatorId(), now));
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.CACHE_ENABLED_CHANGE, AuditResult.SUCCESS).target(TARGET, def.key())
                    .with("from", was).with("to", enabled));
        }
        return view(def, state(now));
    }

    /** 전체 캐시 켜기/끄기 — 캐시별 저장 값은 그대로 남는다. 바뀐 개요 전체를 돌려준다 */
    @Transactional
    public CacheOverviewView setGlobalEnabled(AdminActor actor, boolean enabled) {
        Instant now = Instant.now();
        CacheGlobalSettingEntity global = lockedGlobal(now);
        if (global.isEnabled() != enabled) {
            boolean was = global.isEnabled();
            global.changeEnabled(enabled, actor.operatorId(), now);
            changes.persist(CacheSettingChangeEntity.enabled(ids.nextId(), null, enabled, actor.operatorId(), now));
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.CACHE_GLOBAL_ENABLED_CHANGE, AuditResult.SUCCESS).target(TARGET, null)
                    .with("from", was).with("to", enabled));
        }
        return overview();
    }

    // ── 목록 줄 만들기 ───────────────────────────────────────────────────────

    /** 한 요청 동안 같이 쓰는 값 */
    private record State(Map<String, SettingRow> settings, boolean globalEnabled, Instant globalChangedAt, List<NodeStateRow> nodes, Placements placements,
                         Map<CacheSampleKind, String> samples, BoardCatalogSnapshot boards, List<GadgetPageEntity> gadgetPages, String publicUrl) {

        boolean effective(String key) {
            SettingRow row = settings.get(key);
            return globalEnabled && (row == null || row.enabled());
        }
    }

    private State state(Instant now) {
        Map<String, SettingRow> rows = store.settings().stream().collect(Collectors.toMap(SettingRow::cacheKey, Function.identity()));
        Optional<CacheStateStore.GlobalRow> global = store.global();
        List<NodeStateRow> nodes = store.nodeStates(now.minus(properties.nodeTimeout()));
        Optional<SiteSettingEntity> site = siteSettings.findById(SiteSettingEntity.ROW_ID);
        String theme = SiteThemes.normalize(site.map(SiteSettingEntity::getTheme).orElse(null));
        BoardCatalogSnapshot boards = catalog.snapshot();
        List<GadgetPageEntity> pageRows = pages.findAll().stream().sorted(Comparator.comparing(GadgetPageEntity::getId)).toList();
        String publicUrl = site.map(SiteSettingEntity::getPublicUrl).filter(u -> !u.isBlank()).orElse(null);
        return new State(rows, global.map(CacheStateStore.GlobalRow::enabled).orElse(true), global.map(CacheStateStore.GlobalRow::changedAt).orElse(null), nodes,
                placements(theme, boards, pageRows), samples(boards, now), boards, pageRows, publicUrl);
    }

    private CacheView view(CacheDefinition def, State state) {
        SettingRow row = state.settings().get(def.key());
        int ttl = row == null || row.ttlSeconds() == null ? def.defaultTtlSeconds() : row.ttlSeconds();
        boolean enabled = row == null || row.enabled();
        boolean effective = state.globalEnabled() && enabled;
        long generation = row == null ? 0 : row.generation();
        List<NodeStateRow> nodes = nodesOf(def, state);
        long entries = 0;
        long hits = 0;
        long misses = 0;
        boolean neverCleared = nodes.isEmpty();
        Instant oldestCleared = null;
        List<String> pending = new ArrayList<>();
        for (NodeStateRow n : nodes) {
            entries += n.entryCount();
            hits += n.hitCount();
            misses += n.missCount();
            if (n.lastClearedAt() == null) {
                neverCleared = true;
            } else if (oldestCleared == null || n.lastClearedAt().isBefore(oldestCleared)) {
                oldestCleared = n.lastClearedAt();
            }
            if (n.appliedGeneration() < generation || n.appliedEnabled() != effective) {
                pending.add(n.nodeId());
            }
        }
        Instant pendingSince = null;
        if (!pending.isEmpty()) {
            pendingSince = latest(row == null ? null : row.changedAt(), state.globalChangedAt());
            if (pendingSince == null) {
                pendingSince = Instant.now();
            }
        }
        return new CacheView(def.key(), messages.get(def.nameKey()), messages.get(def.targetKey()), def.targetIsPath(), def.kind().name(),
                def.servers().stream().map(Enum::name).toList(), ttl, def.defaultTtlSeconds(), def.kind().minTtlSeconds(), def.kind().maxTtlSeconds(),
                def.memberMode().name(), entries, hits, misses, generation, neverCleared ? null : oldestCleared, pending.isEmpty() ? "OK" : "PENDING", enabled, effective,
                pending, pendingSince, def.gadgetKind(), usages(def, state), links(def));
    }

    /** 이 캐시를 두는 서버들의 보고(서버 종류가 맞는 것만) */
    private static List<NodeStateRow> nodesOf(CacheDefinition def, State state) {
        return state.nodes().stream()
                .filter(r -> r.cacheKey().equals(def.key()) && def.servers().stream().anyMatch(role -> role.name().equals(r.role())))
                .toList();
    }

    private List<CacheUsageView> usages(CacheDefinition def, State state) {
        if (def.gadgetKind() != null) {
            String name = messages.get(def.nameKey());
            return state.placements().of(def.gadgetKind()).stream()
                    .map(p -> new CacheUsageView(CacheUsageKind.AREA.name(), placeLabel(p), messages.get("cache.gadget.usage.desc", name, p.count()), p.path(), null, false))
                    .toList();
        }
        List<CacheUsageView> out = new ArrayList<>();
        for (int i = 0; i < def.usages().size(); i++) {
            CacheDefinition.Usage u = def.usages().get(i);
            CacheUsageSampleView sample = u.sampleKind() == null ? null
                    : new CacheUsageSampleView(u.sampleKind().name(), u.template(), state.samples().get(u.sampleKind()));
            out.add(new CacheUsageView(u.kind().name(), messages.get(def.usageLabelKey(i)), messages.get(def.usageDescKey(i)), u.path(), sample, u.loginRequired()));
        }
        return out;
    }

    private String placeLabel(CacheGadgetPlacementView p) {
        return switch (p.place()) {
            case Placements.HOME -> p.name();
            case Placements.GADGET_PAGE -> messages.get("cache.place.pageUsage", p.name());
            default -> p.name();
        };
    }

    private List<CacheAdminLinkView> links(CacheDefinition def) {
        return def.adminLinks().stream().map(l -> new CacheAdminLinkView(messages.get(l.labelKey()), l.path())).toList();
    }

    private String source(String code, String detail) {
        if (code == null) {
            return null;
        }
        String text = messages.get("cache.source." + code);
        return detail == null ? text : messages.get("cache.source.withDetail", text, detail);
    }

    private List<CacheExclusionView> exclusions() {
        return UNCACHED.stream()
                .map(kind -> new CacheExclusionView(kind, messages.get("cache.exclusion." + kind + ".name"), messages.get("cache.exclusion." + kind + ".reason"),
                        QUICK_LINKS.equals(kind) || NOTICE_TEXT.equals(kind) ? LAYOUT_CACHES : List.of()))
                .toList();
    }

    // ── 미리보기 쪽 · 예시 ───────────────────────────────────────────────────

    private List<CachePreviewPageView> previewPages(State state) {
        boolean en = english();
        List<CachePreviewPageView> out = new ArrayList<>();
        out.add(new CachePreviewPageView("/", messages.get("cache.preview.home")));
        out.add(new CachePreviewPageView("/latest", messages.get("cache.preview.latest")));
        out.add(new CachePreviewPageView("/popular", messages.get("cache.preview.popular")));
        List<EffectiveBoard> boards = publicBoards(state.boards());
        for (EffectiveBoard b : boards) {
            out.add(new CachePreviewPageView("/boards/" + b.slug(), messages.get("cache.preview.board", nameOf(b.name(), b.nameEn(), en))));
        }
        for (GadgetPageEntity p : state.gadgetPages()) {
            out.add(new CachePreviewPageView(p.publicPath(), messages.get("cache.preview.page", nameOf(p.getName(), p.getNameEn(), en))));
        }
        out.add(new CachePreviewPageView("/boards", messages.get("cache.preview.boards")));
        out.add(new CachePreviewPageView("/me/points", messages.get("cache.preview.points")));
        out.add(new CachePreviewPageView("/notifications", messages.get("cache.preview.notifications")));
        out.add(new CachePreviewPageView("/terms", messages.get("cache.preview.terms")));
        out.add(new CachePreviewPageView("/signup", messages.get("cache.preview.signup")));
        out.add(new CachePreviewPageView("/write", messages.get("cache.preview.write")));
        for (EffectiveBoard b : boards) {
            if (BoardFields.KIND_DEAL.equals(b.kind())) {
                out.add(new CachePreviewPageView("/write?board=" + b.slug(), messages.get("cache.preview.dealWrite", nameOf(b.name(), b.nameEn(), en))));
            }
        }
        out.add(new CachePreviewPageView("/point-shop", messages.get("cache.preview.pointShop")));
        out.add(new CachePreviewPageView("/group-buys", messages.get("cache.preview.groupBuys")));
        return out;
    }

    /** 공개 게시판(켜짐 · 소모임 게시판 아님) — 게시판 차례 */
    private static List<EffectiveBoard> publicBoards(BoardCatalogSnapshot s) {
        return s.boards().stream().filter(b -> b.active() && !BoardFields.KIND_GROUP.equals(b.kind())).toList();
    }

    /** 예시 번호 — 진행 중인 핫딜 최근 글 · 판매 중인 상품 · 모집 중인 공구의 최신 하나(없으면 빠진다) */
    private Map<CacheSampleKind, String> samples(BoardCatalogSnapshot boards, Instant now) {
        Map<CacheSampleKind, String> out = new EnumMap<>(CacheSampleKind.class);
        List<Long> dealBoards = boards.boards().stream().filter(b -> BoardFields.KIND_DEAL.equals(b.kind())).map(EffectiveBoard::id).toList();
        AdminDealQueries.Page page = deals.search(new AdminDealQueries.Filter(dealBoards, null, null, null, null), "ACTIVE", null,
                dealSettings.current().autoExpireDays(), now, 0, 1);
        if (!page.rows().isEmpty()) {
            out.put(CacheSampleKind.HOTDEAL_POST, Long.toString(page.rows().getFirst().postId()));
        }
        samples.latestOnSaleItem(now).ifPresent(id -> out.put(CacheSampleKind.POINT_SHOP_ITEM, Long.toString(id)));
        samples.latestRecruitingGroupBuy().ifPresent(id -> out.put(CacheSampleKind.GROUP_BUY, Long.toString(id)));
        return out;
    }

    // ── 가젯을 놓은 곳 ───────────────────────────────────────────────────────

    /** 지금 사이트 테마의 홈 · 가젯 페이지 · 게시판 레이아웃에 놓인 가젯(저장한 배치, 없으면 처음 배치) */
    private Placements placements(String theme, BoardCatalogSnapshot boards, List<GadgetPageEntity> pageRows) {
        boolean en = english();
        List<Placed> placed = new ArrayList<>();
        List<GadgetSpec> home = homeLayouts.findById(theme).isPresent()
                ? gadgets.findByThemeAndPageIdIsNullAndBoardLayoutFalseOrderByIdAsc(theme).stream().map(GadgetEntity::toSpec).toList()
                : HomeLayoutDefaults.of(theme).gadgets();
        placed.add(new Placed(Placements.HOME, messages.get("cache.place.home"), "/", home));
        for (GadgetPageEntity p : pageRows) {
            List<GadgetSpec> specs = pageLayouts.findByPageIdAndTheme(p.getId(), theme).isPresent()
                    ? gadgets.findByPageIdAndThemeOrderByIdAsc(p.getId(), theme).stream().map(GadgetEntity::toSpec).toList()
                    : GadgetPageInitial.read(p.getInitialLayout(), theme).gadgets();
            placed.add(new Placed(Placements.GADGET_PAGE, nameOf(p.getName(), p.getNameEn(), en), p.publicPath(), specs));
        }
        List<GadgetSpec> boardSpecs = boardLayouts.findById(theme).isPresent()
                ? gadgets.findByThemeAndBoardLayoutTrueOrderByIdAsc(theme).stream().map(GadgetEntity::toSpec).toList()
                : BoardLayoutDefaults.of(theme).home().gadgets();
        String boardPath = publicBoards(boards).stream().findFirst().map(b -> "/boards/" + b.slug()).orElse(null);
        placed.add(new Placed(Placements.BOARD_LAYOUT, messages.get("cache.place.boardLayout"), boardPath, boardSpecs));
        return new Placements(placed);
    }

    private record Placed(String place, String name, String path, List<GadgetSpec> specs) {
    }

    /** 놓은 곳을 가젯 종류별로 센다 */
    private record Placements(List<Placed> placed) {

        static final String HOME = "HOME";
        static final String GADGET_PAGE = "GADGET_PAGE";
        static final String BOARD_LAYOUT = "BOARD_LAYOUT";

        List<CacheGadgetPlacementView> of(String kind) {
            List<CacheGadgetPlacementView> out = new ArrayList<>();
            for (Placed p : placed) {
                int count = (int) p.specs().stream().filter(s -> kind.equals(s.type())).count();
                if (count > 0) {
                    out.add(new CacheGadgetPlacementView(p.place(), p.name(), p.path(), count));
                }
            }
            return out;
        }

        /** 서로 다른 설정 수 — 서버 한 대가 가득 찼을 때의 항목 수(대상 게시판이 쪽마다 달라지는 설정은 더 많을 수 있다) */
        int distinctConfigs(String kind) {
            Set<String> configs = new HashSet<>();
            for (Placed p : placed) {
                p.specs().stream().filter(s -> kind.equals(s.type())).forEach(s -> configs.add(Objects.toString(s.config())));
            }
            return configs.size();
        }
    }

    // ── 도우미 ───────────────────────────────────────────────────────────────

    private static CacheDefinition definition(String cacheKey) {
        return CacheCatalog.find(cacheKey).orElseThrow(CacheNotFoundException::new);
    }

    private boolean effective(String key) {
        boolean global = store.global().map(CacheStateStore.GlobalRow::enabled).orElse(true);
        return global && settings.findById(key).map(CacheSettingEntity::isEnabled).orElse(true);
    }

    private CacheSettingEntity lockedRow(String key, Instant now) {
        return settings.findWithLockByCacheKey(key).orElseGet(() -> {
            CacheSettingEntity created = CacheSettingEntity.defaults(key, now);
            settings.persist(created);
            return created;
        });
    }

    private CacheGlobalSettingEntity lockedGlobal(Instant now) {
        return globals.findWithLockById(CacheGlobalSettingEntity.ROW_ID).orElseGet(() -> {
            CacheGlobalSettingEntity created = CacheGlobalSettingEntity.create(now);
            globals.persist(created);
            return created;
        });
    }

    private boolean english() {
        return "en".equals(messages.locale().getLanguage());
    }

    private static String nameOf(String name, String nameEn, boolean en) {
        return en && nameEn != null && !nameEn.isBlank() ? nameEn : name;
    }

    private static Instant latest(Instant a, Instant b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }

}
