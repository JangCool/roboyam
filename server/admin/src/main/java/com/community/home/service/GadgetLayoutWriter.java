package com.community.home.service;

import com.community.board.service.BoardCatalog;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 홈·가젯 페이지·게시판 레이아웃 배치 저장의 공통 몸통 — 넣고 뺀 결과 전체를 검사하고 가젯 행을 지우고·만들고·고친 뒤 위로 당긴 배치를 돌려준다(배치 행·감사는 부르는 쪽).
 *
 * <ul>
 *   <li>가젯: 저장 가젯 ID 는 그 행을, 시드 ID({@code seed-<n>} — 테마 기본 배치 · 페이지 처음 배치 · 게시판 레이아웃 기본 본문)는 그 시드 값으로 새 행을,
 *       그 밖({@code new-<n>})은 종류별 처음 값(테마 기본 꾸미기 · 처음 설정 · PC·모바일 보임)과 보낸 제목으로 새 행을 만든다. 빠진 저장 가젯은 지운다.
 *       어느 쪽이든 보낸 {@code title}·{@code desktop}·{@code mobile} 로 바꾸고(빠지면 그대로) {@code style}·{@code config} 는 보낸 칸만 덮는다({@link GadgetPatches}).</li>
 *   <li>검사(모아서 {@code 400 VALIDATION}): {@code layout.columns} · {@code gadgets}(한도) · {@code gadgets[i].id|type|title|desktop} · {@code gadgets[i].style.<칸>} ·
 *       {@code gadgets[i].config.<칸>} · {@code layout.items[i]}(없는 가젯·두 번째 자리·범위·최소 크기) · {@code layout.items}(본문 하나 · 자리 없는 가젯 · 겹침).</li>
 *   <li>자리({@link GadgetConfigs.Place}): 대상 게시판 {@code FOLDER} 는 가젯 페이지에서만, {@code BOARD} 는 게시판 레이아웃에서만. 게시판 레이아웃에서만 고정 칸 {@code BOARD_BODY} 를
 *       받는다 — 꼭 하나, 제목·노출·꾸미기·설정은 보내도 쓰지 않는다(고정). 다른 자리에서 {@code BOARD_BODY} 는 모르는 종류다.</li>
 * </ul>
 */
@Component
public class GadgetLayoutWriter {

    private final HomeGadgetRepository gadgets;
    private final SnowflakeIdGenerator ids;
    private final BoardCatalog catalog;

    public GadgetLayoutWriter(HomeGadgetRepository gadgets, SnowflakeIdGenerator ids, BoardCatalog catalog) {
        this.gadgets = gadgets;
        this.ids = ids;
        this.catalog = catalog;
    }

    /**
     * 저장할 곳
     *
     * @param theme  테마(새 가젯의 기본 꾸미기 · 행의 테마)
     * @param place  자리
     * @param pageId 가젯 페이지 ID — 가젯 페이지가 아니면 null
     */
    public record Target(String theme, GadgetConfigs.Place place, Long pageId) {

        public static Target home(String theme) {
            return new Target(theme, GadgetConfigs.Place.HOME, null);
        }

        public static Target page(String theme, long pageId) {
            return new Target(theme, GadgetConfigs.Place.PAGE, pageId);
        }

        public static Target board(String theme) {
            return new Target(theme, GadgetConfigs.Place.BOARD, null);
        }
    }

    /**
     * 저장 결과
     *
     * @param layout        위로 당긴 배치(가젯 ID 는 새 ID 로 바꾼 것)
     * @param configChanged 내용 설정을 바꾼 저장 가젯 수
     * @param changed       무엇이든(제목 · 노출 · 꾸미기 · 내용 설정) 바꾼 저장 가젯 수
     */
    public record Result(HomeGridLayout layout, List<HomeGadgetEntity> gadgets, int added, int removed, int configChanged, int changed) {
    }

    /** 검사하고 가젯 행을 고친다 — 틀리면 아무것도 쓰지 않고 {@link ValidationException} */
    public Result write(Target target, List<HomeGadgetEntity> stored, Function<String, Optional<HomeGadgetSpec>> seedOf, HomeLayoutSaveRequest request, Instant now) {
        return write(target, stored, seedOf, request, now, new LinkedHashMap<>());
    }

    /**
     * @param fields 부르는 쪽이 먼저 모은 칸 오류(게시판 레이아웃의 {@code screens.*}) — 이것과 함께 모아 던진다
     */
    public Result write(Target target, List<HomeGadgetEntity> stored, Function<String, Optional<HomeGadgetSpec>> seedOf, HomeLayoutSaveRequest request, Instant now,
                        Map<String, MessageRef> fields) {
        GadgetConfigs.Place place = target.place();
        boolean inBoard = place == GadgetConfigs.Place.BOARD;
        Map<String, HomeGadgetEntity> storedById = new HashMap<>();
        stored.forEach(g -> storedById.put(Long.toString(g.getId()), g));
        Predicate<String> boardExists = slug -> catalog.snapshot().board(slug).isPresent();

        // ── 가젯: 남길 것 · 시드에서 만들 것 · 새로 만들 것 ──
        List<HomeLayoutSaveRequest.Gadget> wanted = request == null || request.gadgets() == null ? List.of() : request.gadgets();
        if (wanted.size() > HomeGadgetKinds.MAX_GADGETS) {
            fields.put("gadgets", new MessageRef("homeLayout.gadgets.max", String.valueOf(HomeGadgetKinds.MAX_GADGETS)));
        }
        Map<String, String> typeOf = new HashMap<>();
        Set<String> seen = new HashSet<>();
        List<Plan> plans = new ArrayList<>();
        int bodies = 0;
        for (int i = 0; i < wanted.size(); i++) {
            HomeLayoutSaveRequest.Gadget g = wanted.get(i);
            String path = "gadgets[" + i + "]";
            String id = g == null || g.id() == null ? "" : g.id().strip();
            if (id.isEmpty() || !seen.add(id)) {
                fields.put(path + ".id", new MessageRef("homeLayout.gadget.id"));
                continue;
            }
            HomeGadgetEntity keep = storedById.get(id);
            HomeGadgetSpec base;
            boolean created = false;
            if (keep != null) {
                base = keep.toSpec();
            } else {
                base = id.startsWith(HomeLayoutDefaults.SEED_PREFIX) ? seedOf.apply(id).orElse(null) : null;
                if (base == null) {
                    base = fresh(target, id, g, path, fields);
                    created = true;
                    if (base == null) {
                        continue;
                    }
                }
            }
            if (HomeGadgetKinds.BOARD_BODY.equals(base.type())) {
                if (!inBoard) {
                    fields.put(path + ".type", new MessageRef("homeLayout.gadget.type"));
                    continue;
                }
                // 본문은 고정 — 보낸 제목·노출·꾸미기·설정은 쓰지 않는다(저장 본문은 그대로, 시드·새 본문은 고정 값)
                bodies++;
                typeOf.put(id, base.type());
                plans.add(new Plan(id, keep, base));
                continue;
            }
            typeOf.put(id, base.type());
            HomeGadgetSpec next = edited(target, base, g, created, boardExists, path, fields);
            if (next != null) {
                plans.add(new Plan(id, keep, next));
            }
        }

        // ── 배치: 칸 수 · 자리마다 범위 · 본문 하나 · 자리 없는 가젯 · 겹침 ──
        HomeLayoutSaveRequest.Layout layout = request == null ? null : request.layout();
        Integer columns = layout == null ? null : layout.columns();
        boolean columnsOk = columns != null && HomeGadgetKinds.COLUMNS.contains(columns);
        if (!columnsOk) {
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
            if (!columnsOk) {
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
        if (inBoard && bodies != 1) {
            fields.put("layout.items", new MessageRef("boardLayout.body"));
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

        // ── 저장: 빠진 가젯 지우기 → 남긴 가젯 고치기 · 새 가젯 넣기 → 배치 ID 바꾸고 위로 당기기 ──
        Set<String> keptIds = new HashSet<>();
        plans.stream().filter(p -> p.kept() != null).forEach(p -> keptIds.add(p.requestId()));
        List<HomeGadgetEntity> removed = stored.stream().filter(g -> !keptIds.contains(Long.toString(g.getId()))).toList();
        gadgets.deleteAll(removed);
        Map<String, String> rename = new HashMap<>();
        List<HomeGadgetEntity> result = new ArrayList<>();
        int added = 0;
        int configChanged = 0;
        int changed = 0;
        for (Plan p : plans) {
            HomeGadgetSpec next = p.spec();
            if (p.kept() != null) {
                HomeGadgetSpec was = p.kept().toSpec();
                boolean configDiff = !Objects.equals(was.config(), next.config());
                if (configDiff || !Objects.equals(was.title(), next.title()) || was.desktop() != next.desktop() || was.mobile() != next.mobile()
                        || !Objects.equals(was.style(), next.style())) {
                    p.kept().change(next.title(), next.desktop(), next.mobile(), next.style(), next.config());
                    changed++;
                    if (configDiff) {
                        configChanged++;
                    }
                }
                result.add(p.kept());
                continue;
            }
            HomeGadgetEntity created = HomeGadgetEntity.create(ids.nextId(), target.theme(), target.pageId(), place == GadgetConfigs.Place.BOARD, next, now);
            gadgets.persist(created);
            rename.put(p.requestId(), Long.toString(created.getId()));
            result.add(created);
            added++;
        }
        List<HomeLayoutItem> renamed = items.stream()
                .map(it -> new HomeLayoutItem(rename.getOrDefault(it.gadgetId(), it.gadgetId()), it.x(), it.y(), it.w(), it.h())).toList();
        result.sort(Comparator.comparing(HomeGadgetEntity::getId));
        return new Result(new HomeGridLayout(columns, compact(renamed)), result, added, removed.size(), configChanged, changed);
    }

    /** 편집기가 보는 가젯 목록 — 꾸미기는 칸 전부({@link GadgetStyles#view}), 내용 설정은 표 칸 전부({@link GadgetConfigs#view}) */
    public static List<HomeLayoutGadgetView> views(String theme, List<HomeGadgetSpec> specs) {
        return specs.stream().map(s -> new HomeLayoutGadgetView(s.id(), s.type(), s.title(), s.desktop(), s.mobile(), GadgetStyles.view(theme, s.type(), s.style()),
                GadgetConfigs.view(s.type(), s.config()))).toList();
    }

    /** 새 가젯의 처음 값 — 종류·제목 검사(틀리면 모으고 null). 게시판 본문은 게시판 레이아웃에서만, 제목은 고정 */
    private static HomeGadgetSpec fresh(Target target, String id, HomeLayoutSaveRequest.Gadget g, String path, Map<String, MessageRef> fields) {
        String type = g.type() == null ? null : g.type().strip();
        if (HomeGadgetKinds.BOARD_BODY.equals(type) && target.place() == GadgetConfigs.Place.BOARD) {
            return BoardLayoutDefaults.body(id);
        }
        if (!HomeGadgetKinds.isKnown(type)) {
            fields.put(path + ".type", new MessageRef("homeLayout.gadget.type"));
            GadgetPatches.title(g.title(), path, fields);
            return null;
        }
        String title = GadgetPatches.title(g.title(), path, fields);
        if (title == null) {
            return null;
        }
        ObjectNode config = HomeLayoutJson.object();
        if (GadgetConfigs.boards(type)) {
            config.put("boardScope", target.place().initialScope());
            config.putArray("boardIds");
        }
        return new HomeGadgetSpec(id, type, title, true, true, HomeGadgetKinds.baseStyle(target.theme(), type), config, null);
    }

    /** 보낸 제목 · 노출 · 꾸미기 · 내용 설정을 덮은 값 — 틀리면 모으고 null */
    private static HomeGadgetSpec edited(Target target, HomeGadgetSpec base, HomeLayoutSaveRequest.Gadget g, boolean fresh, Predicate<String> boardExists, String path,
                                         Map<String, MessageRef> fields) {
        int errors = fields.size();
        String title = base.title();
        if (!fresh && g.title() != null) {
            title = GadgetPatches.title(g.title(), path, fields);
        }
        boolean desktop = g.desktop() == null ? base.desktop() : g.desktop();
        boolean mobile = g.mobile() == null ? base.mobile() : g.mobile();
        if (!desktop && !mobile) {
            fields.put(path + ".desktop", new MessageRef("homeLayout.gadget.visible"));
        }
        ObjectNode stylePatch = GadgetPatches.style(g.style(), base.style(), path, fields);
        ObjectNode configPatch = GadgetPatches.config(base.type(), g.config(), base.config(), target.place(), boardExists, path, fields);
        if (fields.size() > errors) {
            return null;
        }
        return new HomeGadgetSpec(base.id(), base.type(), title, desktop, mobile, merged(base.style(), stylePatch), merged(base.config(), configPatch), base.more());
    }

    /** 저장 값에 바꿀 칸만 덮는다(다른 칸은 그대로) — 덮을 것이 없으면 저장 값 그대로 */
    private static JsonNode merged(JsonNode base, ObjectNode patch) {
        if (patch == null) {
            return base;
        }
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

    /** 저장할 가젯 하나 — 남길 저장 가젯(없으면 새로 만든다)과 저장할 값 */
    private record Plan(String requestId, HomeGadgetEntity kept, HomeGadgetSpec spec) {
    }
}
