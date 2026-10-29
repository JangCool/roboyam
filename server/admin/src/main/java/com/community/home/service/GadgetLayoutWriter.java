package com.community.home.service;

import com.community.home.dto.HomeGadgetSpec;
import com.community.home.dto.HomeGridLayout;
import com.community.home.dto.HomeLayoutItem;
import com.community.home.dto.request.HomeLayoutSaveRequest;
import com.community.home.dto.response.HomeLayoutGadgetView;
import com.community.home.entity.HomeGadgetEntity;
import com.community.home.repository.master.HomeGadgetRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 홈·가젯 페이지 배치 저장의 공통 몸통 — 넣고 뺀 결과 전체를 검사하고 가젯 행을 지우고·만들고·설정을 바꾼 뒤 위로 당긴 배치를 돌려준다(배치 행·감사는 부르는 쪽).
 *
 * <ul>
 *   <li>가젯: 저장 가젯 ID 는 그대로(제목·노출·꾸미기는 서버 값), 시드 ID({@code seed-<n>} — 테마 기본 배치 또는 페이지의 처음 배치)는 그 시드 그대로,
 *       그 밖({@code new-<n>})은 종류별 처음 값(테마 기본 꾸미기 · 빈 설정 · PC·모바일 보임)과 보낸 제목으로 만든다. 빠진 저장 가젯은 지운다.</li>
 *   <li>검사(모아서 {@code 400 VALIDATION}): {@code layout.columns} · {@code gadgets}(한도) · {@code gadgets[i].id|type|title} · {@code layout.items[i]}(없는 가젯·두 번째 자리·범위·최소 크기) ·
 *       {@code layout.items}(자리 없는 가젯·겹침) · 내용 설정 {@code gadgets[i].config.<칸>}(아래).</li>
 *   <li>내용 설정(⚙ — {@link GadgetConfigs}, gadget-page-contract 「가젯 설정」): 공구 {@code count}·{@code sort} · 글 목록 {@code boardScope}·{@code boardIds}·{@code count}·{@code sort}·{@code periodTabs} ·
 *       갤러리 {@code boardScope}·{@code boardIds}·{@code count} · 게시판 카드 {@code boardScope}·{@code boardIds}·{@code perBoard}·{@code cardHead}. 보내면 그 칸만 저장 설정에 덮고(꾸미기용 다른 칸은 그대로), 보내지 않으면 그대로.
 *       {@code FOLDER}(폴더 게시판 전체)는 가젯 페이지에서만 된다. 다른 종류의 {@code config} 는 무시한다.</li>
 * </ul>
 */
@Component
public class GadgetLayoutWriter {

    /** 직접 고른 게시판 slug 하나의 글자 수 한도 */
    private static final int SLUG_MAX = 50;

    private final HomeGadgetRepository gadgets;
    private final SnowflakeIdGenerator ids;

    public GadgetLayoutWriter(HomeGadgetRepository gadgets, SnowflakeIdGenerator ids) {
        this.gadgets = gadgets;
        this.ids = ids;
    }

    /**
     * 저장 결과
     *
     * @param layout        위로 당긴 배치(가젯 ID 는 새 ID 로 바꾼 것)
     * @param configChanged 설정을 바꾼 저장 가젯 수
     */
    public record Result(HomeGridLayout layout, List<HomeGadgetEntity> gadgets, int added, int removed, int configChanged) {
    }

    /**
     * 검사하고 가젯 행을 고친다 — 틀리면 아무것도 쓰지 않고 {@link ValidationException}.
     *
     * @param themeId 테마(새 가젯의 기본 꾸미기 · 행의 테마)
     * @param pageId  가젯 페이지 ID — 홈이면 null
     * @param stored  지금 저장된 가젯(이 홈 · 이 페이지 테마의 것)
     * @param seedOf  시드 ID → 시드 가젯(테마 기본 배치 · 페이지 처음 배치)
     */
    public Result write(String themeId, Long pageId, List<HomeGadgetEntity> stored, Function<String, Optional<HomeGadgetSpec>> seedOf, HomeLayoutSaveRequest request,
                        Instant now) {
        boolean inPage = pageId != null;
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
                plans.add(new Plan(id, keep, null, configPatch(keep.getType(), g.config(), inPage, path, fields)));
                continue;
            }
            HomeGadgetSpec seed = id.startsWith(HomeLayoutDefaults.SEED_PREFIX) ? seedOf.apply(id).orElse(null) : null;
            if (seed != null) {
                typeOf.put(id, seed.type());
                plans.add(new Plan(id, null, seed, configPatch(seed.type(), g.config(), inPage, path, fields)));
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
                        HomeLayoutJson.object(), null), configPatch(type, g.config(), inPage, path, fields)));
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
            HomeGadgetEntity created = HomeGadgetEntity.create(ids.nextId(), themeId, pageId, spec, now);
            gadgets.persist(created);
            rename.put(p.requestId(), Long.toString(created.getId()));
            result.add(created);
            added++;
        }
        List<HomeLayoutItem> renamed = items.stream()
                .map(it -> new HomeLayoutItem(rename.getOrDefault(it.gadgetId(), it.gadgetId()), it.x(), it.y(), it.w(), it.h())).toList();
        result.sort(Comparator.comparing(HomeGadgetEntity::getId));
        return new Result(new HomeGridLayout(columns, compact(renamed)), result, added, removed.size(), configChanged);
    }

    /** 편집기가 보는 가젯 목록 — 내용 설정은 ⚙ 칸만({@link GadgetConfigs#view}) */
    public static List<HomeLayoutGadgetView> views(List<HomeGadgetSpec> specs) {
        return specs.stream().map(s -> new HomeLayoutGadgetView(s.id(), s.type(), s.title(), s.desktop(), s.mobile(), GadgetConfigs.view(s.type(), s.config()))).toList();
    }

    /**
     * 보낸 내용 설정을 검사한다 — 고칠 수 없는 종류이거나 보내지 않았으면 null(바꾸지 않음). 틀리면 칸 경로로 모으고 null.
     * @return 저장 설정에 덮을 칸 또는 null.
     */
    static ObjectNode configPatch(String type, HomeLayoutSaveRequest.Config raw, boolean inPage, String path, Map<String, MessageRef> fields) {
        if (!GadgetConfigs.settable(type) || raw == null) {
            return null;
        }
        String base = path + ".config.";
        int errors = fields.size();
        ObjectNode out = HomeLayoutJson.object();
        GadgetConfigs.Count count = GadgetConfigs.count(type);
        Integer n = "perBoard".equals(count.field()) ? raw.perBoard() : raw.count();
        if (n == null || n < count.min() || n > count.max()) {
            fields.put(base + count.field(), new MessageRef("perBoard".equals(count.field()) ? "homeLayout.gadget.config.perBoard" : "homeLayout.gadget.config.count",
                    String.valueOf(count.min()), String.valueOf(count.max())));
        } else {
            out.put(count.field(), n);
        }
        List<String> sorts = GadgetConfigs.sorts(type);
        if (!sorts.isEmpty()) {
            if (raw.sort() == null || !sorts.contains(raw.sort())) {
                fields.put(base + "sort", new MessageRef("GROUP_BUY".equals(type) ? "homeLayout.gadget.config.sort" : "homeLayout.gadget.config.postSort"));
            } else {
                out.put("sort", raw.sort());
            }
        }
        if ("POST_LIST".equals(type)) {
            boolean tabs = Boolean.TRUE.equals(raw.periodTabs());
            if (tabs && !"POPULAR".equals(raw.sort())) {
                fields.put(base + "periodTabs", new MessageRef("homeLayout.gadget.config.periodTabs"));
            } else {
                out.put("periodTabs", tabs);
            }
        }
        if ("BOARD_CARDS".equals(type)) {
            out.put("cardHead", !Boolean.FALSE.equals(raw.cardHead()));
        }
        if (GadgetConfigs.boards(type)) {
            String scope = raw.boardScope();
            if (scope == null || !GadgetConfigs.SCOPES.contains(scope) || (GadgetConfigs.FOLDER.equals(scope) && !inPage)) {
                fields.put(base + "boardScope", new MessageRef("homeLayout.gadget.config.boardScope"));
            } else {
                Set<String> slugs = new LinkedHashSet<>();
                boolean bad = false;
                if (GadgetConfigs.PICK.equals(scope)) {
                    for (String s : raw.boardIds() == null ? List.<String>of() : raw.boardIds()) {
                        String slug = s == null ? "" : s.strip();
                        if (slug.isEmpty() || slug.length() > SLUG_MAX) {
                            bad = true;
                        } else {
                            slugs.add(slug);
                        }
                    }
                    if (bad || slugs.isEmpty() || slugs.size() > GadgetConfigs.BOARD_PICK_MAX) {
                        fields.put(base + "boardIds", new MessageRef("homeLayout.gadget.config.boardIds", String.valueOf(GadgetConfigs.BOARD_PICK_MAX)));
                    }
                }
                out.put("boardScope", scope);
                ArrayNode ids = out.putArray("boardIds");
                slugs.forEach(ids::add);
            }
        }
        return fields.size() > errors ? null : out;
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

    /** 저장할 가젯 하나 — 남길 저장 가젯 또는 새로 만들 값, 덮을 내용 설정(없으면 null) */
    private record Plan(String requestId, HomeGadgetEntity kept, HomeGadgetSpec spec, ObjectNode config) {
    }
}
