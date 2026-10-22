package com.community.site.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.notification.service.AdminNotices;
import com.community.permission.dto.AdminActor;
import com.community.site.dto.request.SiteThemeRequest;
import com.community.site.dto.response.SiteThemeResponse;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 사이트 테마({@code site_settings.theme}) 조회·저장. 허용 값은 {@link SiteThemes}(서버 코드, user-web 테마 레지스트리와 같은 id)다.
 * 바뀐 때만 감사 {@code SITE_THEME_UPDATE}(전후 {@code theme})와 관리자 알림(사이트 설정 변경)을 남긴다.
 * 공개 사이트(api)는 자기 사이트 설정 사본({@code PublicSiteService}, 20초)을 다시 읽을 때 따라온다 — 다른 사이트 설정과 같은 전파 방식이다.
 */
@Service
public class SiteThemeService {

    private static final Logger log = LoggerFactory.getLogger(SiteThemeService.class);

    private final SiteSettingRepository settings;
    private final AuditLogService audit;
    private final AdminNotices notices;

    public SiteThemeService(SiteSettingRepository settings, AuditLogService audit, AdminNotices notices) {
        this.settings = settings;
        this.audit = audit;
        this.notices = notices;
    }

    @Transactional(readOnly = true)
    public SiteThemeResponse load() {
        return view(settings.findById(SiteSettingEntity.ROW_ID).orElseThrow(() -> new CommonException(ErrorCode.NOT_FOUND)));
    }

    @Transactional
    public SiteThemeResponse save(AdminActor actor, SiteThemeRequest request) {
        String theme = request == null || request.theme() == null ? null : request.theme().strip();
        if (!SiteThemes.isKnown(theme)) {
            throw ValidationException.of("theme", new MessageRef("siteSetting.theme"));
        }
        SiteSettingEntity row = settings.findWithLockById(SiteSettingEntity.ROW_ID)
                .orElseThrow(() -> ValidationException.of("site", new MessageRef("siteSetting.missing")));
        String before = row.getTheme();
        if (!theme.equals(before)) {
            row.changeTheme(theme, actor.operatorId(), Instant.now());
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_THEME_UPDATE, AuditResult.SUCCESS).target("SITE", "1")
                    .with("before", Map.of("theme", before)).with("after", Map.of("theme", theme)));
            notices.siteSettingsChanged(actor, "siteTheme");
            log.info("사이트 테마를 바꿨다 operatorId={} theme={}", actor.operatorId(), theme);
        }
        return view(row);
    }

    private static SiteThemeResponse view(SiteSettingEntity row) {
        return new SiteThemeResponse(SiteThemes.normalize(row.getTheme()), SiteThemes.IDS);
    }
}
