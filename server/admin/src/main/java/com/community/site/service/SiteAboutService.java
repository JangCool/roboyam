package com.community.site.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.notification.service.AdminNotices;
import com.community.permission.dto.AdminActor;
import com.community.site.dto.request.SiteAboutRequest;
import com.community.site.dto.response.SiteAboutResponse;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이트 소개(공개 사이트 푸터 「사이트 소개」 페이지) 조회·저장 — 사이트 설정 행의 {@code about_html}(V203). 규칙은 {@link SiteAbouts}.
 *
 * <p>바뀐 때만 감사({@code SITE_ABOUT_UPDATE} — 본문 대신 앞뒤 글자 수)와 관리자 알림(사이트 설정 변경, 변형 {@code siteAbout})을 남긴다.
 * 감사 기록이 캐시 {@code site.info} 를 비운다({@code CacheAutoRefresh} — 사이트 설정 저장) — 공개 사이트(api)의 {@code /api/site/info}·{@code /api/site/about} 가 함께 따라온다.
 */
@Service
public class SiteAboutService {

    private static final Logger log = LoggerFactory.getLogger(SiteAboutService.class);

    private final SiteSettingRepository settings;
    private final AuditLogService audit;
    private final AdminNotices notices;

    public SiteAboutService(SiteSettingRepository settings, AuditLogService audit, AdminNotices notices) {
        this.settings = settings;
        this.audit = audit;
        this.notices = notices;
    }

    @Transactional(readOnly = true)
    public SiteAboutResponse load() {
        SiteSettingEntity row = settings.findById(SiteSettingEntity.ROW_ID).orElseThrow(() -> new CommonException(ErrorCode.NOT_FOUND));
        return view(row);
    }

    @Transactional
    public SiteAboutResponse save(AdminActor actor, SiteAboutRequest request) {
        String raw = request == null ? null : request.html();
        if (SiteAbouts.tooLong(raw)) {
            throw ValidationException.of("html", new MessageRef("siteAbout.html.length", String.valueOf(SiteAbouts.HTML_MAX)));
        }
        String html = SiteAbouts.clean(raw);

        SiteSettingEntity row = settings.findWithLockById(SiteSettingEntity.ROW_ID)
                .orElseThrow(() -> ValidationException.of("site", new MessageRef("siteSetting.missing")));
        String before = row.getAboutHtml();
        if (!Objects.equals(before, html)) {
            row.changeAbout(html, actor.operatorId(), Instant.now());
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_ABOUT_UPDATE, AuditResult.SUCCESS).target("SITE", "1")
                    .with("beforeLength", before == null ? 0 : before.length()).with("afterLength", html == null ? 0 : html.length()));
            notices.siteSettingsChanged(actor, "siteAbout");
            log.info("사이트 소개를 바꿨다 operatorId={} length={}", actor.operatorId(), html == null ? 0 : html.length());
        }
        return view(row);
    }

    private static SiteAboutResponse view(SiteSettingEntity row) {
        String publicUrl = row.getPublicUrl();
        return new SiteAboutResponse(row.getAboutHtml(), row.getAboutUpdatedAt(), publicUrl == null || publicUrl.isBlank() ? null : publicUrl);
    }
}
