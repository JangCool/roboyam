package com.community.home.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.home.dto.HomeGridLayout;
import com.community.home.dto.request.BoardLayoutSaveRequest;
import com.community.home.dto.request.HomeLayoutSaveRequest;
import com.community.home.dto.response.BoardLayoutBodyView;
import com.community.home.dto.response.BoardLayoutScreensView;
import com.community.home.dto.response.BoardLayoutView;
import com.community.home.entity.BoardLayoutEntity;
import com.community.home.entity.HomeGadgetEntity;
import com.community.home.repository.master.BoardLayoutRepository;
import com.community.home.repository.master.HomeGadgetRepository;
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
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 게시판 레이아웃(테마마다 하나, 모든 게시판에 같이) 조회·저장 — board-layout-contract. 저장한 적 없는 테마는 기본 배치({@link BoardLayoutDefaults} — 본문만 전체 폭, 두 화면 켬).
 *
 * <ul>
 *   <li>저장은 넣고 뺀 결과 전체다 — 검사·가젯 행 고치기·위로 당기기는 홈과 같은 {@link GadgetLayoutWriter}(자리 {@code BOARD} — 본문 {@code BOARD_BODY} 꼭 하나 · 최소 크기 ·
 *       본문의 제목·노출·꾸미기·설정은 고정 · 대상 게시판 {@code BOARD} 는 여기서만, {@code FOLDER} 는 400) + 적용 화면 {@code screens.list|post}(불리언).
 *       같은 테마의 저장은 홈처럼 사이트 설정 행 잠금(+ 배치 행 잠금)으로 줄 세운다.</li>
 *   <li>저장마다 감사 {@code BOARD_LAYOUT_UPDATE}(테마 · 칸 수·가젯 수·적용 화면 전후 · 넣은·뺀 수 · 설정·무엇이든 바꾼 가젯 수)와 관리자 알림(사이트 설정 변경, 변형 {@code boardLayout}).
 *       공개 {@code GET /api/boards/{slug}/layout} 은 요청마다 읽는다.</li>
 * </ul>
 */
@Service
public class BoardLayoutAdminService {

    private static final Logger log = LoggerFactory.getLogger(BoardLayoutAdminService.class);

    private final BoardLayoutRepository layouts;
    private final HomeGadgetRepository gadgets;
    private final SiteSettingRepository settings;
    private final OperatorRepository operators;
    private final GadgetLayoutWriter writer;
    private final AuditLogService audit;
    private final AdminNotices notices;

    public BoardLayoutAdminService(BoardLayoutRepository layouts, HomeGadgetRepository gadgets, SiteSettingRepository settings, OperatorRepository operators,
                                   GadgetLayoutWriter writer, AuditLogService audit, AdminNotices notices) {
        this.layouts = layouts;
        this.gadgets = gadgets;
        this.settings = settings;
        this.operators = operators;
        this.writer = writer;
        this.audit = audit;
        this.notices = notices;
    }

    @Transactional(readOnly = true)
    public BoardLayoutView get(String theme) {
        String id = known(theme);
        BoardLayoutEntity row = layouts.findById(id).orElse(null);
        if (row == null) {
            BoardLayoutDefaults.Board board = BoardLayoutDefaults.of(id);
            return new BoardLayoutView(id, screens(board.screens()), board.home().layout(), GadgetLayoutWriter.views(id, board.home().gadgets()), null, null);
        }
        return view(row, gadgets.findByThemeAndBoardLayoutTrueOrderByIdAsc(id));
    }

    public BoardLayoutBodyView defaults(String theme) {
        String id = known(theme);
        BoardLayoutDefaults.Board board = BoardLayoutDefaults.of(id);
        return new BoardLayoutBodyView(screens(board.screens()), board.home().layout(), GadgetLayoutWriter.views(id, board.home().gadgets()));
    }

    @Transactional
    public BoardLayoutView save(AdminActor actor, String theme, BoardLayoutSaveRequest request) {
        String themeId = known(theme);
        settings.findWithLockById(SiteSettingEntity.ROW_ID).orElseThrow(() -> ValidationException.of("site", new MessageRef("siteSetting.missing")));
        BoardLayoutEntity row = layouts.findWithLockByTheme(themeId).orElse(null);
        List<HomeGadgetEntity> stored = row == null ? List.of() : gadgets.findByThemeAndBoardLayoutTrueOrderByIdAsc(themeId);
        BoardLayoutDefaults.Board seeds = BoardLayoutDefaults.of(themeId);

        Map<String, MessageRef> fields = new LinkedHashMap<>();
        BoardLayoutSaveRequest.Screens sent = request == null ? null : request.screens();
        Boolean list = sent == null ? null : sent.list();
        Boolean post = sent == null ? null : sent.post();
        if (list == null) {
            fields.put("screens.list", new MessageRef("boardLayout.screens"));
        }
        if (post == null) {
            fields.put("screens.post", new MessageRef("boardLayout.screens"));
        }
        Instant now = Instant.now();
        HomeLayoutSaveRequest body = request == null ? null : new HomeLayoutSaveRequest(request.layout(), request.gadgets());
        GadgetLayoutWriter.Result result = writer.write(GadgetLayoutWriter.Target.board(themeId), stored, seeds.home()::gadget, body, now, fields);

        HomeGridLayout saved = result.layout();
        HomeGridLayout before = row == null ? seeds.home().layout() : row.toLayout();
        boolean listBefore = row == null ? seeds.screens().list() : row.isListEnabled();
        boolean postBefore = row == null ? seeds.screens().post() : row.isPostEnabled();
        if (row == null) {
            row = BoardLayoutEntity.create(themeId, list, post, saved, actor.operatorId(), now);
            layouts.persist(row);
        } else {
            row.change(list, post, saved, actor.operatorId(), now);
        }

        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.BOARD_LAYOUT_UPDATE, AuditResult.SUCCESS).target("SITE", "1")
                .with("theme", themeId)
                .with("before", Map.of("columns", before.columns(), "gadgets", before.items().size(), "list", listBefore, "post", postBefore))
                .with("after", Map.of("columns", saved.columns(), "gadgets", saved.items().size(), "list", list, "post", post))
                .with("added", result.added()).with("removed", result.removed()).with("configChanged", result.configChanged()).with("changed", result.changed()));
        notices.siteSettingsChanged(actor, "boardLayout");
        log.info("게시판 레이아웃을 저장했다 operatorId={} theme={} gadgets={} added={} removed={}", actor.operatorId(), themeId, result.gadgets().size(), result.added(),
                result.removed());
        return view(row, result.gadgets());
    }

    /** 고를 수 있는 테마인가 — 아니면 404 */
    private static String known(String theme) {
        if (!SiteThemes.isKnown(theme)) {
            throw new CommonException(ErrorCode.NOT_FOUND);
        }
        return theme;
    }

    private static BoardLayoutScreensView screens(BoardLayoutDefaults.Screens screens) {
        return new BoardLayoutScreensView(screens.list(), screens.post());
    }

    private BoardLayoutView view(BoardLayoutEntity row, List<HomeGadgetEntity> rows) {
        String updatedBy = row.getUpdatedBy() == null ? null : operators.findById(row.getUpdatedBy()).map(OperatorEntity::getOperatorName).orElse(null);
        return new BoardLayoutView(row.getTheme(), new BoardLayoutScreensView(row.isListEnabled(), row.isPostEnabled()), row.toLayout(),
                GadgetLayoutWriter.views(row.getTheme(), rows.stream().map(HomeGadgetEntity::toSpec).toList()), row.getUpdatedAt(), updatedBy);
    }
}
