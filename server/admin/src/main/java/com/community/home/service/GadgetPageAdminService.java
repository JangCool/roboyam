package com.community.home.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.home.dto.HomeGridLayout;
import com.community.home.dto.request.GadgetPageSaveRequest;
import com.community.home.dto.request.HomeLayoutSaveRequest;
import com.community.home.dto.response.GadgetPageLayoutView;
import com.community.home.dto.response.GadgetPageListResponse;
import com.community.home.dto.response.GadgetPageView;
import com.community.home.dto.response.HomeLayoutBodyView;
import com.community.home.entity.GadgetPageEntity;
import com.community.home.entity.GadgetPageLayoutEntity;
import com.community.home.entity.HomeGadgetEntity;
import com.community.home.exception.GadgetPageNotFoundException;
import com.community.home.repository.master.GadgetPageLayoutRepository;
import com.community.home.repository.master.GadgetPageRepository;
import com.community.home.repository.master.HomeGadgetRepository;
import com.community.menu.repository.master.MenuNodeRepositoryQDSL;
import com.community.menu.service.MenuBoardLinker;
import com.community.menu.target.GadgetPageMenuTargetResolver;
import com.community.notification.service.AdminNotices;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.site.service.SiteThemes;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 가젯 페이지 — 목록·만들기·고치기·지우기와 테마별 배치 조회·저장(docs/features/gadget-page-contract.md).
 *
 * <ul>
 *   <li>만들기: 기준 폴더의 켜진 게시판으로 처음 배치({@link GadgetPageInitial})를 한 번 만든다. 기준 폴더를 바꿔도 처음 배치는 다시 만들지 않는다.</li>
 *   <li>검사(모아서 {@code 400}): {@code key}(모양 · 겹침) · {@code name}(1~40자) · {@code nameEn}(40자) · {@code description}(200자) · {@code folderId}(main 의 폴더) · {@code pages}(한도).</li>
 *   <li>지우기: 페이지 · 모든 테마 배치 · 가젯(FK 연쇄)을 지우고, 이 페이지를 가리키는 메뉴 항목은 지우고 폴더는 연결만 비운다({@link MenuBoardLinker#pageDeleted}).</li>
 *   <li>배치: 저장한 적 없는 테마는 처음 배치(그 테마의 기본 꾸미기). 저장은 홈과 같은 {@link GadgetLayoutWriter}(시드 = 처음 배치, 대상 게시판 {@code FOLDER} 를 받는다).
 *       같은 페이지의 저장·고치기는 페이지 행 잠금으로 줄 세운다.</li>
 *   <li>감사 {@code GADGET_PAGE_CREATE|UPDATE|DELETE|LAYOUT_UPDATE} · 관리자 알림 사이트 설정 변경(변형 {@code gadgetPage}).</li>
 * </ul>
 */
@Service
public class GadgetPageAdminService {

    private static final Logger log = LoggerFactory.getLogger(GadgetPageAdminService.class);
    private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9-]{1,29}$");
    static final int NAME_MAX = 40;
    static final int DESCRIPTION_MAX = 200;
    static final int MAX_PAGES = 50;

    private final GadgetPageRepository pages;
    private final GadgetPageLayoutRepository layouts;
    private final HomeGadgetRepository gadgets;
    private final GadgetPageFolders folders;
    private final MenuNodeRepositoryQDSL nodeQueries;
    private final MenuBoardLinker linker;
    private final OperatorRepository operators;
    private final SnowflakeIdGenerator ids;
    private final GadgetLayoutWriter writer;
    private final AuditLogService audit;
    private final AdminNotices notices;

    public GadgetPageAdminService(GadgetPageRepository pages, GadgetPageLayoutRepository layouts, HomeGadgetRepository gadgets, GadgetPageFolders folders,
                                  MenuNodeRepositoryQDSL nodeQueries, MenuBoardLinker linker, OperatorRepository operators, SnowflakeIdGenerator ids,
                                  GadgetLayoutWriter writer, AuditLogService audit, AdminNotices notices) {
        this.pages = pages;
        this.layouts = layouts;
        this.gadgets = gadgets;
        this.folders = folders;
        this.nodeQueries = nodeQueries;
        this.linker = linker;
        this.operators = operators;
        this.ids = ids;
        this.writer = writer;
        this.audit = audit;
        this.notices = notices;
    }

    @Transactional(readOnly = true)
    public GadgetPageListResponse list() {
        Map<Long, GadgetPageFolders.Folder> all = folders.all();
        return new GadgetPageListResponse(pages.findAllByOrderByIdAsc().stream().map(p -> view(p, all)).toList());
    }

    @Transactional
    public GadgetPageView create(AdminActor actor, GadgetPageSaveRequest request) {
        Map<Long, GadgetPageFolders.Folder> all = folders.all();
        GadgetPageEntity.Fields fields = validate(request, null, all);
        Instant now = Instant.now();
        GadgetPageFolders.Folder folder = fields.folderId() == null ? null : all.get(fields.folderId());
        List<GadgetPageFolders.Board> boards = folder == null ? List.of() : folder.boards().stream().filter(GadgetPageFolders.Board::active).toList();
        String initial = HomeLayoutJson.write(GadgetPageInitial.build(folder == null ? null : folder.name(), boards));
        GadgetPageEntity row = GadgetPageEntity.create(ids.nextId(), fields, initial, actor.operatorId(), now);
        pages.persist(row);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.GADGET_PAGE_CREATE, AuditResult.SUCCESS)
                .target("GADGET_PAGE", Long.toString(row.getId()))
                .with("key", fields.key()).with("name", fields.name()).with("folderId", fields.folderId() == null ? "" : fields.folderId().toString()));
        notices.siteSettingsChanged(actor, "gadgetPage");
        log.info("가젯 페이지를 만들었다 operatorId={} id={} key={} boards={}", actor.operatorId(), row.getId(), fields.key(), boards.size());
        return view(row, all);
    }

    @Transactional
    public GadgetPageView update(AdminActor actor, long id, GadgetPageSaveRequest request) {
        GadgetPageEntity row = pages.findWithLockById(id).orElseThrow(GadgetPageNotFoundException::new);
        Map<Long, GadgetPageFolders.Folder> all = folders.all();
        GadgetPageEntity.Fields next = validate(request, id, all);
        GadgetPageEntity.Fields before = row.fields();
        if (!next.equals(before)) {
            row.change(next, actor.operatorId(), Instant.now());
            Map<String, Object> was = new LinkedHashMap<>();
            Map<String, Object> now = new LinkedHashMap<>();
            diff("key", before.key(), next.key(), was, now);
            diff("name", before.name(), next.name(), was, now);
            diff("nameEn", before.nameEn(), next.nameEn(), was, now);
            diff("description", before.description(), next.description(), was, now);
            diff("folderId", before.folderId(), next.folderId(), was, now);
            diff("writeButton", before.writeButton(), next.writeButton(), was, now);
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.GADGET_PAGE_UPDATE, AuditResult.SUCCESS)
                    .target("GADGET_PAGE", Long.toString(id)).with("before", was).with("after", now));
            notices.siteSettingsChanged(actor, "gadgetPage");
        }
        return view(row, all);
    }

    @Transactional
    public void delete(AdminActor actor, long id) {
        GadgetPageEntity row = pages.findWithLockById(id).orElseThrow(GadgetPageNotFoundException::new);
        Instant now = Instant.now();
        List<String> menuSets = linker.pageDeleted(id, now);
        pages.delete(row);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.GADGET_PAGE_DELETE, AuditResult.SUCCESS)
                .target("GADGET_PAGE", Long.toString(id)).with("key", row.getPageKey()).with("name", row.getName()).with("menuSets", menuSets));
        notices.siteSettingsChanged(actor, "gadgetPage");
        log.info("가젯 페이지를 지웠다 operatorId={} id={} key={} menuSets={}", actor.operatorId(), id, row.getPageKey(), menuSets);
    }

    @Transactional(readOnly = true)
    public GadgetPageLayoutView layout(long id, String theme) {
        GadgetPageEntity page = pages.findById(id).orElseThrow(GadgetPageNotFoundException::new);
        String themeId = known(theme);
        GadgetPageLayoutEntity row = layouts.findByPageIdAndTheme(id, themeId).orElse(null);
        if (row == null) {
            HomeLayoutDefaults.Home initial = GadgetPageInitial.read(page.getInitialLayout(), themeId);
            return new GadgetPageLayoutView(Long.toString(id), themeId, initial.layout(), GadgetLayoutWriter.views(initial.gadgets()), null, null);
        }
        return view(row, gadgets.findByPageIdAndThemeOrderByIdAsc(id, themeId));
    }

    @Transactional(readOnly = true)
    public HomeLayoutBodyView initial(long id) {
        GadgetPageEntity page = pages.findById(id).orElseThrow(GadgetPageNotFoundException::new);
        HomeLayoutDefaults.Home initial = GadgetPageInitial.read(page.getInitialLayout(), SiteThemes.DEFAULT);
        return new HomeLayoutBodyView(initial.layout(), GadgetLayoutWriter.views(initial.gadgets()));
    }

    @Transactional
    public GadgetPageLayoutView saveLayout(AdminActor actor, long id, String theme, HomeLayoutSaveRequest request) {
        GadgetPageEntity page = pages.findWithLockById(id).orElseThrow(GadgetPageNotFoundException::new);
        String themeId = known(theme);
        GadgetPageLayoutEntity row = layouts.findByPageIdAndTheme(id, themeId).orElse(null);
        List<HomeGadgetEntity> stored = gadgets.findByPageIdAndThemeOrderByIdAsc(id, themeId);
        HomeLayoutDefaults.Home seeds = GadgetPageInitial.read(page.getInitialLayout(), themeId);
        Instant now = Instant.now();
        GadgetLayoutWriter.Result result = writer.write(themeId, id, stored, seeds::gadget, request, now);

        HomeGridLayout saved = result.layout();
        HomeGridLayout before = row == null ? seeds.layout() : row.toLayout();
        if (row == null) {
            row = GadgetPageLayoutEntity.create(id, themeId, saved, actor.operatorId(), now);
            layouts.persist(row);
        } else {
            row.change(saved, actor.operatorId(), now);
        }
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.GADGET_PAGE_LAYOUT_UPDATE, AuditResult.SUCCESS)
                .target("GADGET_PAGE", Long.toString(id))
                .with("theme", themeId)
                .with("before", Map.of("columns", before.columns(), "gadgets", before.items().size()))
                .with("after", Map.of("columns", saved.columns(), "gadgets", saved.items().size()))
                .with("added", result.added()).with("removed", result.removed()).with("configChanged", result.configChanged()));
        notices.siteSettingsChanged(actor, "gadgetPage");
        log.info("가젯 페이지 배치를 저장했다 operatorId={} id={} theme={} gadgets={}", actor.operatorId(), id, themeId, result.gadgets().size());
        return view(row, result.gadgets());
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    /** 칸 검사 — 틀리면 칸 경로로 모아 400. 통과하면 다듬은 값 */
    private GadgetPageEntity.Fields validate(GadgetPageSaveRequest request, Long selfId, Map<Long, GadgetPageFolders.Folder> all) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        String key = request == null || request.key() == null ? "" : request.key().strip();
        String name = request == null || request.name() == null ? "" : request.name().strip();
        String nameEn = request == null || request.nameEn() == null ? "" : request.nameEn().strip();
        String description = request == null || request.description() == null ? "" : request.description().strip();
        if (selfId == null && pages.count() >= MAX_PAGES) {
            fields.put("pages", new MessageRef("gadgetPage.max", String.valueOf(MAX_PAGES)));
        }
        if (!KEY.matcher(key).matches()) {
            fields.put("key", new MessageRef("gadgetPage.key"));
        } else if (selfId == null ? pages.existsByPageKey(key) : pages.existsByPageKeyAndIdNot(key, selfId)) {
            fields.put("key", new MessageRef("gadgetPage.key.taken"));
        }
        if (name.isEmpty() || length(name) > NAME_MAX || hasControl(name)) {
            fields.put("name", new MessageRef("gadgetPage.name", String.valueOf(NAME_MAX)));
        }
        if (length(nameEn) > NAME_MAX || hasControl(nameEn)) {
            fields.put("nameEn", new MessageRef("gadgetPage.nameEn", String.valueOf(NAME_MAX)));
        }
        if (length(description) > DESCRIPTION_MAX || hasControl(description)) {
            fields.put("description", new MessageRef("gadgetPage.description", String.valueOf(DESCRIPTION_MAX)));
        }
        Long folderId = null;
        String rawFolder = request == null || request.folderId() == null ? "" : request.folderId().strip();
        if (!rawFolder.isEmpty()) {
            try {
                folderId = Long.parseLong(rawFolder);
            } catch (NumberFormatException e) {
                folderId = null;
            }
            if (folderId == null || !all.containsKey(folderId)) {
                fields.put("folderId", new MessageRef("gadgetPage.folder"));
            }
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        boolean writeButton = request.writeButton() == null || request.writeButton();
        return new GadgetPageEntity.Fields(key, name, nameEn.isEmpty() ? null : nameEn, description, folderId, writeButton);
    }

    private GadgetPageView view(GadgetPageEntity page, Map<Long, GadgetPageFolders.Folder> all) {
        GadgetPageFolders.Folder folder = page.getFolderId() == null ? null : all.get(page.getFolderId());
        List<GadgetPageView.Board> boards = folder == null ? List.of()
                : folder.boards().stream().map(b -> new GadgetPageView.Board(b.slug(), b.name(), b.nameEn(), b.layout())).toList();
        return new GadgetPageView(Long.toString(page.getId()), page.getPageKey(), page.getName(), page.getNameEn(), page.getDescription(),
                page.getFolderId() == null ? null : page.getFolderId().toString(), folder == null ? null : folder.name(), boards, page.isWriteButton(),
                nodeQueries.countLinking(GadgetPageMenuTargetResolver.TYPE, page.getId()), page.getUpdatedAt(), operatorName(page.getUpdatedBy()));
    }

    private GadgetPageLayoutView view(GadgetPageLayoutEntity row, List<HomeGadgetEntity> rows) {
        return new GadgetPageLayoutView(Long.toString(row.getPageId()), row.getTheme(), row.toLayout(),
                GadgetLayoutWriter.views(rows.stream().map(HomeGadgetEntity::toSpec).toList()), row.getUpdatedAt(), operatorName(row.getUpdatedBy()));
    }

    private String operatorName(Long operatorId) {
        return operatorId == null ? null : operators.findById(operatorId).map(OperatorEntity::getOperatorName).orElse(null);
    }

    /** 고를 수 있는 테마인가 — 아니면 404 */
    private static String known(String theme) {
        if (!SiteThemes.isKnown(theme)) {
            throw new CommonException(ErrorCode.NOT_FOUND);
        }
        return theme;
    }

    private static void diff(String field, Object before, Object after, Map<String, Object> was, Map<String, Object> now) {
        if (!Objects.equals(before, after)) {
            was.put(field, before == null ? "" : before);
            now.put(field, after == null ? "" : after);
        }
    }

    private static int length(String value) {
        return value.codePointCount(0, value.length());
    }

    private static boolean hasControl(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }
}
