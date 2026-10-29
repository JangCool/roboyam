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
 *   <li>저장은 넣고 뺀 결과 전체다 — 검사·가젯 행 고치기·위로 당기기는 가젯 페이지와 같은 {@link GadgetLayoutWriter}(시드 = 테마 기본 배치). 같은 테마의 저장은 사이트 설정 행 잠금으로 줄 세운다.</li>
 *   <li>⚙ 설정(8종 내용 + 제목·노출·꾸미기, gadget-page-contract 「가젯 설정」)은 저장 가젯도 바꾸고 꾸미기·설정은 보낸 칸만 덮는다. 홈에서는 대상 게시판 {@code FOLDER}·{@code BOARD} 를 받지 않는다.
 *       응답의 {@code style}·{@code config} 는 칸 전부({@link GadgetStyles#view}·{@link GadgetConfigs#view}).</li>
 *   <li>저장마다 감사 {@code HOME_LAYOUT_UPDATE}(테마 · 칸 수·가젯 수 전후 · 넣은·뺀 수 · 설정을 바꾼 가젯 수 {@code configChanged} · 무엇이든 바꾼 가젯 수 {@code changed})와
 *       관리자 알림(사이트 설정 변경). 공개 홈({@code GET /api/home/gadgets})은 요청마다 읽는다.</li>
 * </ul>
 */
@Service
public class HomeLayoutAdminService {

    private static final Logger log = LoggerFactory.getLogger(HomeLayoutAdminService.class);

    private final HomeLayoutRepository layouts;
    private final HomeGadgetRepository gadgets;
    private final SiteSettingRepository settings;
    private final OperatorRepository operators;
    private final GadgetLayoutWriter writer;
    private final AuditLogService audit;
    private final AdminNotices notices;

    public HomeLayoutAdminService(HomeLayoutRepository layouts, HomeGadgetRepository gadgets, SiteSettingRepository settings, OperatorRepository operators,
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
    public HomeLayoutView get(String theme) {
        String id = known(theme);
        HomeLayoutEntity row = layouts.findById(id).orElse(null);
        if (row == null) {
            HomeLayoutDefaults.Home home = HomeLayoutDefaults.of(id);
            return new HomeLayoutView(id, home.layout(), GadgetLayoutWriter.views(id, home.gadgets()), null, null);
        }
        return view(row, gadgets.findByThemeAndPageIdIsNullAndBoardLayoutFalseOrderByIdAsc(id));
    }

    public HomeLayoutBodyView defaults(String theme) {
        String id = known(theme);
        HomeLayoutDefaults.Home home = HomeLayoutDefaults.of(id);
        return new HomeLayoutBodyView(home.layout(), GadgetLayoutWriter.views(id, home.gadgets()));
    }

    @Transactional
    public HomeLayoutView save(AdminActor actor, String theme, HomeLayoutSaveRequest request) {
        String themeId = known(theme);
        settings.findWithLockById(SiteSettingEntity.ROW_ID).orElseThrow(() -> ValidationException.of("site", new MessageRef("siteSetting.missing")));
        HomeLayoutEntity row = layouts.findWithLockByTheme(themeId).orElse(null);
        List<HomeGadgetEntity> stored = row == null ? List.of() : gadgets.findByThemeAndPageIdIsNullAndBoardLayoutFalseOrderByIdAsc(themeId);
        HomeLayoutDefaults.Home seeds = HomeLayoutDefaults.of(themeId);
        Instant now = Instant.now();
        GadgetLayoutWriter.Result result = writer.write(GadgetLayoutWriter.Target.home(themeId), stored, seeds::gadget, request, now);

        HomeGridLayout saved = result.layout();
        HomeGridLayout before = row == null ? seeds.layout() : row.toLayout();
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
                .with("added", result.added()).with("removed", result.removed()).with("configChanged", result.configChanged()).with("changed", result.changed()));
        notices.siteSettingsChanged(actor, "homeLayout");
        log.info("홈 구성을 저장했다 operatorId={} theme={} gadgets={} added={} removed={}", actor.operatorId(), themeId, result.gadgets().size(), result.added(),
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

    private HomeLayoutView view(HomeLayoutEntity row, List<HomeGadgetEntity> rows) {
        String updatedBy = row.getUpdatedBy() == null ? null : operators.findById(row.getUpdatedBy()).map(OperatorEntity::getOperatorName).orElse(null);
        return new HomeLayoutView(row.getTheme(), row.toLayout(), GadgetLayoutWriter.views(row.getTheme(), rows.stream().map(HomeGadgetEntity::toSpec).toList()), row.getUpdatedAt(),
                updatedBy);
    }

}
