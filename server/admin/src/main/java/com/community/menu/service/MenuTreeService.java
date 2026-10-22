package com.community.menu.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.grade.entity.GradeEntity;
import com.community.menu.dto.MenuBoardRef;
import com.community.menu.dto.MenuNode;
import com.community.menu.dto.MenuViewer;
import com.community.menu.dto.request.MenuPreviewRequest;
import com.community.menu.dto.request.MenuTreeSaveRequest;
import com.community.menu.dto.response.MenuBoardOptionView;
import com.community.menu.dto.response.MenuGradeOptionView;
import com.community.menu.dto.response.MenuResponse;
import com.community.menu.dto.response.MenuTargetOptionsResponse;
import com.community.menu.dto.response.MenuTargetTypeView;
import com.community.menu.dto.response.MenuTreeResponse;
import com.community.menu.entity.MenuSetEntity;
import com.community.menu.exception.MenuSetNotFoundException;
import com.community.menu.exception.MenuVersionConflictException;
import com.community.menu.repository.master.MenuNodeRepositoryQDSL;
import com.community.menu.repository.master.MenuSetRepository;
import com.community.menu.target.MenuResolveContext;
import com.community.menu.target.MenuTargetRegistry;
import com.community.menu.target.MenuValidationContext;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메뉴 그룹 트리 조회·전체 교체·미리보기와 대상 고르기 재료 (docs/features/menu-contract.md, admin-web {@code useMenuApi.ts} 머리말).
 *
 * <ul>
 *   <li>저장은 <b>트리 전체 교체</b>다 — 요청에 없는 기존 노드는 지우고, {@code id: null}·그룹에 없는 ID 는 새 노드다. 그룹 행을 잠근 뒤 {@code version} 을 견주고
 *       다르면 409 {@code MENU_VERSION_CONFLICT}. 검사·정리는 {@link MenuTreeValidator}(400 {@code VALIDATION}, {@code fields["nodes[1].children[2].label"]}).</li>
 *   <li>바뀐 것이 없으면 버전을 올리지 않고 감사 기록도 남기지 않는다. 바뀌면 {@code MENU_TREE_UPDATE} 에 전·후 트리 요약을 남긴다.</li>
 *   <li>미리보기는 저장하지 않고 같은 검사를 거친 뒤 공개 응답과 같은 조립기({@link MenuViewComposer})로 거른다.</li>
 * </ul>
 */
@Service
public class MenuTreeService {

    private static final Logger log = LoggerFactory.getLogger(MenuTreeService.class);

    private final MenuSetRepository sets;
    private final MenuNodeRepositoryQDSL nodeQueries;
    private final MenuTreeStore store;
    private final MenuTreeValidator validator;
    private final MenuViewComposer composer;
    private final MenuSnapshots snapshots;
    private final MenuTargetRegistry registry;
    private final MenuViews views;
    private final Messages messages;
    private final AuditLogService audit;

    public MenuTreeService(MenuSetRepository sets, MenuNodeRepositoryQDSL nodeQueries, MenuTreeStore store, MenuTreeValidator validator,
                           MenuViewComposer composer, MenuSnapshots snapshots, MenuTargetRegistry registry, MenuViews views, Messages messages,
                           AuditLogService audit) {
        this.sets = sets;
        this.nodeQueries = nodeQueries;
        this.store = store;
        this.validator = validator;
        this.composer = composer;
        this.snapshots = snapshots;
        this.registry = registry;
        this.views = views;
        this.messages = messages;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public MenuTreeResponse tree(AdminActor actor, String key) {
        MenuSetEntity set = find(key);
        List<MenuNode> tree = store.load(set.getSetKey());
        return response(set, tree, statusContext(), actor);
    }

    @Transactional
    public MenuTreeResponse save(AdminActor actor, String key, MenuTreeSaveRequest request) {
        MenuSetEntity set = key == null ? notFound() : sets.findWithLockBySetKey(key).orElseGet(MenuTreeService::notFound);
        if (request == null || request.version() == null || !request.version().equals(Long.toString(set.getVersion()))) {
            throw new MenuVersionConflictException();
        }
        if (request.nodes() == null) {
            throw ValidationException.of("nodes", new MessageRef("menu.tree.required"));
        }
        Map<Long, MenuBoardRef> boards = snapshots.boards();
        List<GradeEntity> grades = snapshots.grades();
        Map<Long, Boolean> stored = nodeQueries.autoAddById(set.getSetKey());
        List<MenuNode> wanted = validator.validate(MenuViews.fromRequest(request.nodes()), validationContext(set, boards, grades), stored);

        MenuResolveContext statusContext = new MenuResolveContext(boards, MenuSnapshots.levels(grades), MenuSales.enabled(), null);
        List<MenuNode> current = store.load(set.getSetKey());
        if (wanted.equals(current)) {
            return response(set, current, statusContext, actor);
        }
        Instant now = Instant.now();
        List<MenuNode> saved = store.replace(set.getSetKey(), wanted, new HashSet<>(stored.keySet()));
        set.treeChanged(actor.operatorId(), now);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.MENU_TREE_UPDATE, AuditResult.SUCCESS).target("MENU_SET", set.getSetKey())
                .with("before", Map.of("nodeCount", MenuNode.count(current), "tree", MenuViews.outline(current)))
                .with("after", Map.of("nodeCount", MenuNode.count(saved), "tree", MenuViews.outline(saved))));
        log.info("메뉴 트리를 저장했다 key={} 노드={} by operatorId={}", set.getSetKey(), MenuNode.count(saved), actor.operatorId());
        return response(set, saved, statusContext, actor);
    }

    /** 저장 전 트리를 이 사람 기준으로 걸러 본다 — 공개 {@code GET /api/menu} 와 같은 모양(저장하지 않는다) */
    @Transactional(readOnly = true)
    public MenuResponse preview(String key, MenuPreviewRequest request) {
        MenuSetEntity set = find(key);
        Map<Long, MenuBoardRef> boards = snapshots.boards();
        List<GradeEntity> grades = snapshots.grades();
        MenuViewer viewer = viewer(request == null ? null : request.viewer(), grades);
        List<MenuNode> tree = validator.validate(MenuViews.fromRequest(request == null ? null : request.nodes()), validationContext(set, boards, grades),
                nodeQueries.autoAddById(set.getSetKey()));
        MenuResolveContext context = new MenuResolveContext(boards, MenuSnapshots.levels(grades), MenuSales.enabled(), viewer);
        return composer.compose(tree, MenuLimits.isSide(set.getPlacement()), context, null);
    }

    @Transactional(readOnly = true)
    public MenuTargetOptionsResponse targetOptions(String key) {
        find(key);
        List<MenuBoardOptionView> boards = snapshots.boards().values().stream()
                .map(board -> new MenuBoardOptionView(Long.toString(board.id()), board.slug(), board.name(), board.nameEn(), board.active())).toList();
        List<MenuGradeOptionView> grades = snapshots.grades().stream().map(grade -> new MenuGradeOptionView(grade.getGradeKey(), grade.getLabel(), grade.getLevel()))
                .toList();
        return new MenuTargetOptionsResponse(boards, grades, MenuSales.enabled());
    }

    /** 연결 유형 표 — 등록된 리졸버(고를 수 있음) + 준비 중 유형 */
    public List<MenuTargetTypeView> targetTypes() {
        List<MenuTargetTypeView> out = new ArrayList<>();
        registry.all().forEach(resolver -> out.add(new MenuTargetTypeView(resolver.type(), typeLabel(resolver.type()), resolver.picker(), true)));
        MenuTargetRegistry.SOON_TYPES.forEach(type -> out.add(new MenuTargetTypeView(type, typeLabel(type), null, false)));
        return out;
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private MenuTreeResponse response(MenuSetEntity set, List<MenuNode> tree, MenuResolveContext context, AdminActor actor) {
        return new MenuTreeResponse(MenuViews.setView(set, MenuNode.count(tree)), views.nodes(tree, context), Long.toString(set.getVersion()),
                actor.can(Permission.LAYOUT_SETTINGS));
    }

    private MenuResolveContext statusContext() {
        return new MenuResolveContext(snapshots.boards(), Map.of(), MenuSales.enabled(), null);
    }

    private static MenuValidationContext validationContext(MenuSetEntity set, Map<Long, MenuBoardRef> boards, List<GradeEntity> grades) {
        return new MenuValidationContext(boards, new HashSet<>(MenuSnapshots.levels(grades).keySet()), set.getPlacement());
    }

    /** 미리보기의 보는 사람 — {@code MEMBER} 는 등급 키(없으면 첫 등급)의 서열 */
    private static MenuViewer viewer(MenuPreviewRequest.Viewer viewer, List<GradeEntity> grades) {
        String kind = viewer == null ? null : viewer.kind();
        if (MenuViewer.GUEST.equals(kind)) {
            return MenuViewer.guest();
        }
        if (MenuViewer.STAFF.equals(kind)) {
            return new MenuViewer(MenuViewer.STAFF, null);
        }
        if (!MenuViewer.MEMBER.equals(kind)) {
            throw ValidationException.of("viewer.kind", new MessageRef("menu.preview.viewer"));
        }
        if (viewer.gradeKey() == null || viewer.gradeKey().isBlank()) {
            return new MenuViewer(MenuViewer.MEMBER, grades.isEmpty() ? 1 : grades.get(0).getLevel());
        }
        return grades.stream().filter(grade -> grade.getGradeKey().equals(viewer.gradeKey().strip())).findFirst()
                .map(grade -> new MenuViewer(MenuViewer.MEMBER, grade.getLevel()))
                .orElseThrow(() -> ValidationException.of("viewer.gradeKey", new MessageRef("menu.grade.unknown")));
    }

    private String typeLabel(String type) {
        return messages.get(new MessageRef("menu.targetType." + type));
    }

    private MenuSetEntity find(String key) {
        return key == null ? notFound() : sets.findById(key).orElseGet(MenuTreeService::notFound);
    }

    private static MenuSetEntity notFound() {
        throw new MenuSetNotFoundException();
    }
}
