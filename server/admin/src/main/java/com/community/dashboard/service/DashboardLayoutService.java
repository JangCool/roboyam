package com.community.dashboard.service;

import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.DashboardGadgetType.Option;
import com.community.dashboard.dto.request.DashboardLayoutRequest;
import com.community.dashboard.dto.response.DashboardLayoutView;
import com.community.dashboard.repository.master.DashboardLayoutStore;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 내 대시보드 구성(admin-dashboard-contract 「구성(저장 값)」) — 로그인한 운영자 <b>본인</b>의 것만, 별도 권한 없음, 감사 기록 없음(개인 화면 설정).
 *
 * <p>저장 검사는 화면의 읽기 검증({@code UtilDashboardLayout.read}·{@code readConfig})과 같은 규칙이고, 틀리면 {@code 400 VALIDATION} 의 {@code fields.gadgets[i].<칸>} 이다.
 * 화면은 틀린 값을 범위 안으로 고쳐 읽지만 서버는 고쳐 저장하지 않고 거절한다. 설정({@code config})만은 화면처럼 <b>종류에 없는 칸·모르는 칸을 버린다</b>
 * (종류에 있는 칸의 값이 틀리면 거절). 빈 제목·빈 메모·빈 바로가기·새로 고침 0(끔)은 칸이 없는 것과 같아 버린다.
 * 겹침·빈 줄(위로 당기기)은 검사하지 않는다 — 화면이 읽을 때 당긴다.
 */
@Service
public class DashboardLayoutService {

    static final int VERSION = 2;
    static final int MAX_GADGETS = 40;
    static final int ID_MAX = 40;
    static final int MAX_Y = 2000;
    static final int MIN_H = 5;
    static final int MAX_H = 80;
    static final int TITLE_MAX = 40;
    static final int TEXT_MAX = 1000;
    static final int LINKS_MAX = 12;
    /** 바로가기 주소 한 개의 길이 한도(가정 — 관리자 화면 주소는 이보다 훨씬 짧다) */
    static final int LINK_MAX = 300;
    static final Set<String> COLORS = Set.of("NONE", "SLATE", "BLUE", "SKY", "TEAL", "GREEN", "AMBER", "ORANGE", "ROSE", "VIOLET");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final DashboardLayoutStore store;

    public DashboardLayoutService(DashboardLayoutStore store) {
        this.store = store;
    }

    /** 내 구성 — 저장한 적 없으면 {@code gadgets: null} */
    @Transactional(readOnly = true)
    public DashboardLayoutView get(AdminActor actor) {
        return store.find(actor.operatorId()).map(row -> new DashboardLayoutView(row.version(), row.columns(), read(row.gadgets())))
                .orElseGet(() -> new DashboardLayoutView(VERSION, DashboardGadgetType.COLUMNS, null));
    }

    /** 구성을 검사해 저장하고 저장한 값(정리한 설정)을 돌려준다 */
    @Transactional
    public DashboardLayoutView save(AdminActor actor, DashboardLayoutRequest body) {
        List<DashboardLayoutView.Gadget> gadgets = check(body);
        ArrayNode out = MAPPER.createArrayNode();
        for (DashboardLayoutView.Gadget g : gadgets) {
            out.add(MAPPER.createObjectNode().put("id", g.id()).put("type", g.type()).put("x", g.x()).put("y", g.y()).put("w", g.w()).put("h", g.h())
                    .set("config", g.config()));
        }
        store.save(actor.operatorId(), VERSION, DashboardGadgetType.COLUMNS, MAPPER.writeValueAsString(out), Instant.now());
        return new DashboardLayoutView(VERSION, DashboardGadgetType.COLUMNS, gadgets);
    }

    // ── 검사 ────────────────────────────────────────────────────────────────

    private static List<DashboardLayoutView.Gadget> check(DashboardLayoutRequest body) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        if (body == null || body.version() == null || body.version() != VERSION) {
            fields.put("version", new MessageRef("dashboard.layout.version"));
        }
        if (body == null || body.columns() == null || body.columns() != DashboardGadgetType.COLUMNS) {
            fields.put("columns", new MessageRef("dashboard.layout.columns", String.valueOf(DashboardGadgetType.COLUMNS)));
        }
        if (body == null || body.gadgets() == null) {
            fields.put("gadgets", new MessageRef("dashboard.layout.gadgets.required"));
            throw new ValidationException(fields);
        }
        if (body.gadgets().size() > MAX_GADGETS) {
            fields.put("gadgets", new MessageRef("dashboard.layout.gadgets.max", String.valueOf(MAX_GADGETS)));
            throw new ValidationException(fields);
        }
        Set<String> ids = new HashSet<>();
        Set<DashboardGadgetType> singles = EnumSet.noneOf(DashboardGadgetType.class);
        List<DashboardLayoutView.Gadget> out = new ArrayList<>();
        for (int i = 0; i < body.gadgets().size(); i++) {
            String path = "gadgets[" + i + "]";
            DashboardLayoutRequest.Gadget g = body.gadgets().get(i);
            if (g == null) {
                fields.put(path, new MessageRef("dashboard.gadget.type"));
                continue;
            }
            if (g.id() == null || g.id().isEmpty() || g.id().length() > ID_MAX || !ids.add(g.id())) {
                fields.put(path + ".id", new MessageRef("dashboard.gadget.id", String.valueOf(ID_MAX)));
            }
            DashboardGadgetType type = DashboardGadgetType.of(g.type());
            if (type == null) {
                fields.put(path + ".type", new MessageRef("dashboard.gadget.type"));
                continue;
            }
            if (!type.multiple() && !singles.add(type)) {
                fields.put(path + ".type", new MessageRef("dashboard.gadget.single"));
            }
            int columns = DashboardGadgetType.COLUMNS;
            boolean wOk = g.w() != null && g.w() >= type.minW() && g.w() <= columns;
            if (!wOk) {
                fields.put(path + ".w", new MessageRef("dashboard.gadget.w", String.valueOf(type.minW()), String.valueOf(columns)));
            }
            if (g.x() == null || g.x() < 0 || g.x() > columns - 1 || (wOk && g.x() + g.w() > columns)) {
                fields.put(path + ".x", new MessageRef("dashboard.gadget.x"));
            }
            if (g.y() == null || g.y() < 0 || g.y() > MAX_Y) {
                fields.put(path + ".y", new MessageRef("dashboard.gadget.y", String.valueOf(MAX_Y)));
            }
            if (g.h() == null || g.h() < MIN_H || g.h() > MAX_H) {
                fields.put(path + ".h", new MessageRef("dashboard.gadget.h", String.valueOf(MIN_H), String.valueOf(MAX_H)));
            }
            ObjectNode config = config(g.config(), type, path + ".config", fields);
            if (fields.isEmpty()) {
                out.add(new DashboardLayoutView.Gadget(g.id(), type.name(), g.x(), g.y(), g.w(), g.h(), config));
            }
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return out;
    }

    /** 설정 — 종류에 있는 칸만 검사·정리하고(키 차례 고정) 나머지 칸은 버린다. null·빈 칸은 없는 것과 같다 */
    private static ObjectNode config(JsonNode raw, DashboardGadgetType type, String path, Map<String, MessageRef> fields) {
        ObjectNode out = MAPPER.createObjectNode();
        if (raw == null || raw.isNull()) {
            return out;
        }
        if (!raw.isObject()) {
            fields.put(path, new MessageRef("dashboard.gadget.config"));
            return out;
        }
        JsonNode title = present(raw, "title");
        if (title != null) {
            String value = title.isString() ? title.asString().strip() : null;
            if (value == null || value.length() > TITLE_MAX) {
                fields.put(path + ".title", new MessageRef("dashboard.gadget.config.title", String.valueOf(TITLE_MAX)));
            } else if (!value.isEmpty()) {
                out.put("title", value);
            }
        }
        JsonNode refresh = type.refreshable() ? present(raw, "refresh") : null;
        if (refresh != null) {
            Integer value = integer(refresh);
            if (value == null || (value != 0 && value != 60 && value != 300)) {
                fields.put(path + ".refresh", new MessageRef("dashboard.gadget.config.refresh"));
            } else if (value != 0) {
                out.put("refresh", value);
            }
        }
        JsonNode limit = type.has(Option.LIMIT) ? present(raw, "limit") : null;
        if (limit != null) {
            Integer value = integer(limit);
            if (value == null || (value != 5 && value != 10)) {
                fields.put(path + ".limit", new MessageRef("dashboard.gadget.config.limit"));
            } else {
                out.put("limit", value);
            }
        }
        JsonNode days = type.has(Option.DAYS) ? present(raw, "days") : null;
        if (days != null) {
            Integer value = integer(days);
            if (value == null || (value != 7 && value != 30)) {
                fields.put(path + ".days", new MessageRef("dashboard.gadget.config.days"));
            } else {
                out.put("days", value);
            }
        }
        JsonNode text = type.has(Option.TEXT) ? present(raw, "text") : null;
        if (text != null) {
            if (!text.isString() || text.asString().length() > TEXT_MAX) {
                fields.put(path + ".text", new MessageRef("dashboard.gadget.config.text", String.valueOf(TEXT_MAX)));
            } else if (!text.asString().isEmpty()) {
                out.put("text", text.asString());
            }
        }
        JsonNode links = type.has(Option.LINKS) ? present(raw, "links") : null;
        if (links != null) {
            Set<String> values = new LinkedHashSet<>();
            boolean ok = links.isArray();
            if (ok) {
                for (JsonNode link : links) {
                    if (!link.isString() || !link.asString().startsWith("/") || link.asString().length() > LINK_MAX) {
                        ok = false;
                        break;
                    }
                    values.add(link.asString());
                }
            }
            if (!ok || values.size() > LINKS_MAX) {
                fields.put(path + ".links", new MessageRef("dashboard.gadget.config.links", String.valueOf(LINKS_MAX)));
            } else if (!values.isEmpty()) {
                ArrayNode arr = out.putArray("links");
                values.forEach(arr::add);
            }
        }
        JsonNode color = present(raw, "color");
        if (color != null) {
            if (!color.isString() || !COLORS.contains(color.asString())) {
                fields.put(path + ".color", new MessageRef("dashboard.gadget.config.color"));
            } else {
                out.put("color", color.asString());
            }
        }
        return out;
    }

    /** 칸 값 — 없거나 JSON null 이면 null */
    private static JsonNode present(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value;
    }

    /** 정수 값(소수·글자는 null) */
    private static Integer integer(JsonNode node) {
        return node.isIntegralNumber() && node.canConvertToInt() ? node.intValue() : null;
    }

    // ── 읽기 ────────────────────────────────────────────────────────────────

    /** 저장된 배열을 그대로 돌려준다(저장할 때 검사·정리했다). 모양이 깨진 줄은 건너뛴다 */
    private static List<DashboardLayoutView.Gadget> read(String json) {
        List<DashboardLayoutView.Gadget> out = new ArrayList<>();
        JsonNode node = json == null ? null : MAPPER.readTree(json);
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode it : node) {
            if (!it.isObject() || !it.path("id").isString() || !it.path("type").isString()) {
                continue;
            }
            JsonNode config = it.get("config");
            out.add(new DashboardLayoutView.Gadget(it.path("id").asString(), it.path("type").asString(), it.path("x").asInt(), it.path("y").asInt(),
                    it.path("w").asInt(), it.path("h").asInt(), config != null && config.isObject() ? config : MAPPER.createObjectNode()));
        }
        return out;
    }
}
