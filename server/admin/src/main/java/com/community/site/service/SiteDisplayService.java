package com.community.site.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.permission.dto.AdminActor;
import com.community.site.dto.request.SiteDisplayDefaultsRequest;
import com.community.site.dto.response.DisplayValuesView;
import com.community.site.dto.response.SiteDisplayDefaultsResponse;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 표시 기본값(언어·시간대·시간 형식·날짜 형식) 조회·저장. 개인 설정의 「기본 설정」이 되는 값이다(내 표시 형식을 쓰지 않는 운영자 화면이 이 값을 따른다 — 내 정보의 {@code inherited}).
 * 허용 값과 칸별 검사는 {@link DisplayValuesPolicy} 다(내 표시 형식과 같은 규칙).
 *
 * <p><b>저장 위치(가정)</b>: 정책 미확정이라 사이트 설정 행({@code site_settings.display_*})에 둔다 — 사이트 전체에 하나의 값이다. 공개 사이트 회원의 사이트 기본 표시 값과 같은 값인지는 정해지지 않았다.
 */
@Service
public class SiteDisplayService {

    private static final Logger log = LoggerFactory.getLogger(SiteDisplayService.class);

    private final SiteSettingRepository settings;
    private final AuditLogService audit;

    public SiteDisplayService(SiteSettingRepository settings, AuditLogService audit) {
        this.settings = settings;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public SiteDisplayDefaultsResponse load() {
        return view(settings.findById(SiteSettingEntity.ROW_ID).orElseThrow(() -> new CommonException(ErrorCode.NOT_FOUND)));
    }

    @Transactional
    public SiteDisplayDefaultsResponse save(AdminActor actor, SiteDisplayDefaultsRequest request) {
        DisplayValuesPolicy.Checked values = DisplayValuesPolicy.check(request == null ? null : request.values());
        String language = values.language();
        String zoneId = values.zoneId();
        String timeFormat = values.timeFormat();
        String dateFormat = values.dateFormat();

        SiteSettingEntity row = settings.findWithLockById(SiteSettingEntity.ROW_ID)
                .orElseThrow(() -> ValidationException.of("site", new MessageRef("siteSetting.missing")));
        boolean changed = !language.equals(row.getDisplayLanguage()) || !zoneId.equals(row.getDisplayZoneId())
                || !timeFormat.equals(row.getDisplayTimeFormat()) || !dateFormat.equals(row.getDisplayDateFormat());
        if (changed) {
            String before = row.getDisplayLanguage() + "/" + row.getDisplayZoneId() + "/" + row.getDisplayTimeFormat() + "/" + row.getDisplayDateFormat();
            row.changeDisplay(language, zoneId, timeFormat, dateFormat, actor.operatorId(), Instant.now());
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_DISPLAY_UPDATE, AuditResult.SUCCESS).target("SITE", "1")
                    .with("from", before).with("to", language + "/" + zoneId + "/" + timeFormat + "/" + dateFormat));
            log.info("표시 기본값을 바꿨다 operatorId={}", actor.operatorId());
        }
        return view(row);
    }

    private static SiteDisplayDefaultsResponse view(SiteSettingEntity row) {
        return new SiteDisplayDefaultsResponse(new DisplayValuesView(row.getDisplayLanguage(), row.getDisplayZoneId(), row.getDisplayTimeFormat(), row.getDisplayDateFormat()));
    }
}
