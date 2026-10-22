package com.community.menu.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.menu.dto.AdminMenuNode;
import com.community.menu.dto.request.AdminMenuNodeRequest;
import com.community.menu.dto.request.AdminMenuSaveRequest;
import com.community.menu.dto.response.AdminMenuConfigResponse;
import com.community.menu.dto.response.AdminMenuNodeView;
import com.community.menu.exception.MenuVersionConflictException;
import com.community.menu.repository.master.AdminMenuQueries;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 사이트 메뉴 구성 — 두 모드(일반·설정)를 한 번에 읽고 통째로 바꾼다 (docs/features/menu-contract.md 「관리자 사이트 메뉴」, admin-web {@code UtilAdminMenuConfig}).
 *
 * <ul>
 *   <li>저장한 적이 없으면 화면 레지스트리의 기본 구성({@link AdminScreenRegistry#defaults()}, ID 없음)을 돌려준다.</li>
 *   <li>구조: 묶음({@code SECTION})은 최상위만, 부모 항목({@code FOLDER})은 최상위나 묶음 안만, 화면 항목({@code SCREEN})은 하위를 갖지 않는다 — 어기면 {@code fields["modes.<모드>"]}.</li>
 *   <li>칸: 이름(묶음·부모는 기본 이름이 없으면 필수, 외부 링크는 필수, 1~20자) · 영문명 · 아이콘 {@code pi pi-이름}(부모 항목 안의 화면은 아이콘을 지운다) ·
 *       관리자 화면 키(레지스트리에 있는 것, 두 모드 통틀어 한 번) · 외부 링크 주소({@link MenuRules#hrefProblem}) · 외부 링크의 권한(카탈로그에 있는 코드).
 *       화면 항목의 권한은 레지스트리가 정해 저장하지 않는다. 경로는 {@code modes.normal[0].children[1].label} 꼴.</li>
 *   <li>상태 행을 잠근 뒤 {@code version} 을 견주고 다르면 409 {@code MENU_VERSION_CONFLICT}. 감사 기록 {@code ADMIN_MENU_UPDATE}(전·후 요약).</li>
 *   <li>이번 단계에서 관리자 셸은 저장된 구성을 읽지 않는다(admin-web 메뉴 정의를 그대로 쓴다).</li>
 * </ul>
 */
@Service
public class AdminMenuService {

    private static final Logger log = LoggerFactory.getLogger(AdminMenuService.class);
    private static final Pattern ICON = Pattern.compile("^pi pi-[a-z0-9-]+$");
    private static final int ICON_MAX = 60;
    private static final int LABEL_MAX = 20;
    /** 두 모드 합친 노드 수 상한(지금 기본 구성은 40개 남짓) */
    private static final int NODES_MAX = 200;

    private final AdminMenuQueries queries;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;

    public AdminMenuService(AdminMenuQueries queries, SnowflakeIdGenerator ids, AuditLogService audit) {
        this.queries = queries;
        this.ids = ids;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AdminMenuConfigResponse get(AdminActor actor) {
        AdminMenuQueries.State state = queries.state();
        return response(current(state), state.version(), actor);
    }

    @Transactional
    public AdminMenuConfigResponse save(AdminActor actor, AdminMenuSaveRequest request) {
        AdminMenuQueries.State state = queries.lockState();
        if (request == null || request.version() == null || !request.version().equals(Long.toString(state.version()))) {
            throw new MenuVersionConflictException();
        }
        if (request.modes() == null || request.modes().normal() == null || request.modes().settings() == null) {
            throw ValidationException.of("modes", new MessageRef("adminMenu.modes.required"));
        }
        Map<String, List<AdminMenuNodeRequest>> raw = new LinkedHashMap<>();
        raw.put(AdminScreenRegistry.NORMAL, request.modes().normal());
        raw.put(AdminScreenRegistry.SETTINGS, request.modes().settings());
        Map<String, List<AdminMenuNode>> wanted = validate(raw);

        Map<String, List<AdminMenuNode>> before = current(state);
        boolean saved = state.updatedAt() != null;
        if (saved && wanted.equals(before)) {
            return response(before, state.version(), actor);
        }
        Set<Long> existing = new HashSet<>();
        if (saved) {
            before.values().forEach(nodes -> collectIds(nodes, existing));
        }
        Set<Long> used = new HashSet<>();
        Map<String, List<AdminMenuNode>> after = queries.replace(wanted,
                node -> node.id() != null && existing.contains(node.id()) && used.add(node.id()) ? node.id() : ids.nextId());
        Instant now = Instant.now();
        queries.saved(actor.operatorId(), now);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.ADMIN_MENU_UPDATE, AuditResult.SUCCESS).target("ADMIN_MENU", "config")
                .with("before", summary(before)).with("after", summary(after)));
        log.info("관리자 메뉴 구성을 저장했다 by operatorId={}", actor.operatorId());
        return response(after, state.version() + 1, actor);
    }

    // ── 검사 ────────────────────────────────────────────────────────────────

    private Map<String, List<AdminMenuNode>> validate(Map<String, List<AdminMenuNodeRequest>> raw) {
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        Set<String> screens = new HashSet<>();
        Map<String, List<AdminMenuNode>> out = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<String, List<AdminMenuNodeRequest>> entry : raw.entrySet()) {
            String root = "modes." + entry.getKey();
            List<AdminMenuNode> nodes = walk(entry.getValue(), root, null, root, screens, errors);
            total += AdminMenuNode.count(nodes);
            out.put(entry.getKey(), nodes);
        }
        if (total > NODES_MAX) {
            errors.putIfAbsent("modes", new MessageRef("adminMenu.limit", String.valueOf(NODES_MAX)));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return out;
    }

    /** @param parentKind 부모 종류(최상위면 null) · {@code modeKey} 구조 오류를 거는 칸({@code modes.normal}) */
    private List<AdminMenuNode> walk(List<AdminMenuNodeRequest> nodes, String prefix, String parentKind, String modeKey, Set<String> screens,
                                     Map<String, MessageRef> errors) {
        List<AdminMenuNode> out = new ArrayList<>();
        if (nodes == null) {
            return out;
        }
        for (int i = 0; i < nodes.size(); i++) {
            String path = prefix + "[" + i + "]";
            MenuFieldErrors fieldErrors = new MenuFieldErrors(path, errors);
            AdminMenuNodeRequest node = nodes.get(i);
            String kind = node == null ? null : node.kind();
            if (!AdminMenuNode.SECTION.equals(kind) && !AdminMenuNode.FOLDER.equals(kind) && !AdminMenuNode.SCREEN.equals(kind)) {
                fieldErrors.add("kind", new MessageRef("menu.node.kind"));
                continue;
            }
            boolean screen = AdminMenuNode.SCREEN.equals(kind);
            // 구조 — 묶음은 최상위만, 부모 항목은 최상위·묶음 안만
            if ((AdminMenuNode.SECTION.equals(kind) && parentKind != null)
                    || (AdminMenuNode.FOLDER.equals(kind) && parentKind != null && !AdminMenuNode.SECTION.equals(parentKind))) {
                errors.putIfAbsent(modeKey, new MessageRef("adminMenu.structure"));
            }
            if (screen && node.children() != null && !node.children().isEmpty()) {
                fieldErrors.add("children", new MessageRef("menu.node.children"));
            }
            String labelKey = screen ? null : MenuRules.stripToNull(node.labelKey());
            if (labelKey != null && !AdminScreenRegistry.isGroupLabelKey(labelKey)) {
                fieldErrors.add("labelKey", new MessageRef("adminMenu.labelKey.unknown"));
            }
            String targetType = screen ? node.targetType() : null;
            boolean url = AdminMenuNode.URL.equals(targetType);
            boolean labelRequired = screen ? url : labelKey == null;
            fieldErrors.add("label", MenuRules.labelProblem(node.label(), labelRequired, LABEL_MAX));
            fieldErrors.add("labelEn", MenuRules.nameEnProblem(node.labelEn()));
            boolean underFolder = AdminMenuNode.FOLDER.equals(parentKind);
            String icon = underFolder ? null : MenuRules.stripToNull(node.icon());
            if (icon != null && (icon.length() > ICON_MAX || !ICON.matcher(icon).matches())) {
                fieldErrors.add("icon", new MessageRef("adminMenu.icon.format"));
            }
            String targetKey = null;
            String href = null;
            boolean newTab = true;
            String permission = null;
            if (screen) {
                if (AdminMenuNode.ADMIN_SCREEN.equals(targetType)) {
                    targetKey = MenuRules.stripToNull(node.targetKey());
                    if (targetKey == null) {
                        fieldErrors.add("targetKey", new MessageRef("menu.target.required"));
                    } else if (AdminScreenRegistry.screen(targetKey).isEmpty()) {
                        fieldErrors.add("targetKey", new MessageRef("adminMenu.screen.unknown"));
                    } else if (!screens.add(targetKey)) {
                        fieldErrors.add("targetKey", new MessageRef("adminMenu.screen.duplicate"));
                    }
                } else if (url) {
                    String rawHref = node.settings() == null ? null : node.settings().href();
                    fieldErrors.add("settings.href", MenuRules.hrefProblem(rawHref));
                    href = rawHref == null ? null : rawHref.strip();
                    newTab = node.settings() == null || !Boolean.FALSE.equals(node.settings().newTab());
                    permission = MenuRules.stripToNull(node.permission());
                    if (permission != null && Permission.ofCode(permission).isEmpty()) {
                        fieldErrors.add("permission", new MessageRef("adminMenu.permission.unknown"));
                    }
                } else {
                    fieldErrors.add("targetType", new MessageRef("menu.target.type"));
                }
            }
            List<AdminMenuNode> children = screen ? List.of() : walk(node.children(), path + ".children", kind, modeKey, screens, errors);
            Long id = parseId(node.id());
            out.add(new AdminMenuNode(id, kind, labelKey, MenuRules.stripToNull(node.label()), MenuRules.stripToNull(node.labelEn()), icon,
                    !Boolean.FALSE.equals(node.visible()), screen ? targetType : null, targetKey, href, newTab, permission, children));
        }
        return out;
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    /** 저장된 구성(저장한 적 없으면 레지스트리 기본 구성) */
    private Map<String, List<AdminMenuNode>> current(AdminMenuQueries.State state) {
        if (state.updatedAt() == null) {
            return AdminScreenRegistry.defaults();
        }
        Map<String, List<AdminMenuNode>> stored = queries.load();
        Map<String, List<AdminMenuNode>> out = new LinkedHashMap<>();
        AdminScreenRegistry.MODES.forEach(mode -> out.put(mode, stored.getOrDefault(mode, List.of())));
        return out;
    }

    private static AdminMenuConfigResponse response(Map<String, List<AdminMenuNode>> modes, long version, AdminActor actor) {
        return new AdminMenuConfigResponse(new AdminMenuConfigResponse.Modes(views(modes.get(AdminScreenRegistry.NORMAL)),
                views(modes.get(AdminScreenRegistry.SETTINGS))), Long.toString(version), actor.can(Permission.ADMIN_MENU_MANAGE));
    }

    private static List<AdminMenuNodeView> views(List<AdminMenuNode> nodes) {
        if (nodes == null) {
            return List.of();
        }
        return nodes.stream().map(node -> {
            boolean url = AdminMenuNode.URL.equals(node.targetType());
            return new AdminMenuNodeView(node.id() == null ? null : Long.toString(node.id()), node.kind(), node.labelKey(), node.label(), node.labelEn(),
                    node.icon(), node.visible(), node.targetType(), node.targetKey(), url ? new AdminMenuNodeView.Link(node.href(), node.newTab()) : null,
                    url ? node.permission() : null, node.isScreen() ? null : views(node.children()));
        }).toList();
    }

    private static void collectIds(List<AdminMenuNode> nodes, Set<Long> sink) {
        for (AdminMenuNode node : nodes) {
            sink.add(node.id());
            collectIds(node.children(), sink);
        }
    }

    /** 감사 기록용 요약 — 모드별 노드 수와 한 줄 요약 */
    private static Map<String, Object> summary(Map<String, List<AdminMenuNode>> modes) {
        Map<String, Object> out = new LinkedHashMap<>();
        modes.forEach((mode, nodes) -> {
            List<String> lines = new ArrayList<>();
            outline(nodes, 0, lines);
            out.put(mode, lines);
        });
        return out;
    }

    private static void outline(List<AdminMenuNode> nodes, int depth, List<String> lines) {
        for (AdminMenuNode node : nodes) {
            String name = node.label() != null ? node.label() : node.labelKey() != null ? node.labelKey() : "";
            String target = AdminMenuNode.ADMIN_SCREEN.equals(node.targetType()) ? node.targetKey() : AdminMenuNode.URL.equals(node.targetType()) ? node.href() : null;
            lines.add("  ".repeat(depth) + node.kind() + " " + name + (target == null ? "" : " → " + target) + (node.visible() ? "" : " [hidden]"));
            outline(node.children(), depth + 1, lines);
        }
    }

    private static Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
