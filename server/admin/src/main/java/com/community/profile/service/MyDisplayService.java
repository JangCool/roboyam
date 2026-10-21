package com.community.profile.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.operator.entity.OperatorPreferenceEntity;
import com.community.operator.repository.master.OperatorPreferenceRepository;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.profile.dto.request.MyDisplayRequest;
import com.community.profile.dto.response.MyDisplayResponse;
import com.community.site.dto.response.DisplayValuesView;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.site.service.DisplayValuesPolicy;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 표시 형식(언어·시간대·시간 형식·날짜 형식) — 층은 <b>사이트 표시 기본값 ← 내 표시 형식</b> 둘이다. 「내 표시 형식을 쓴다」를 켜면 내 값이 이기고, 끄면 사이트 기본값을 따른다. 꺼도 내 값은 남는다.
 *
 * <ul>
 *   <li>{@code inherited} 는 요청 때마다 사이트 설정 행({@code site_settings.display_*})에서 읽는다 — 사이트 기본값을 바꾸면 다음 조회부터 바로 따라온다.</li>
 *   <li>값 허용 범위와 칸별 검사 문구는 사이트 표시 기본값과 같은 {@link DisplayValuesPolicy} 다. 「쓴다」({@code use}) 가 빠졌으면 {@code fields.use}.</li>
 *   <li>한 번도 저장하지 않았으면 {@code use=false}, {@code values} 는 {@code inherited} 와 같다. 저장하면 운영자 행을 잠근 뒤 설정 행을 만들거나 고친다. 바뀐 때만 감사 {@code PROFILE_DISPLAY_UPDATE}.</li>
 * </ul>
 */
@Service
public class MyDisplayService {

    private static final Logger log = LoggerFactory.getLogger(MyDisplayService.class);

    private final OperatorPreferenceRepository preferences;
    private final OperatorRepository operators;
    private final SiteSettingRepository siteSettings;
    private final AuditLogService audit;

    public MyDisplayService(OperatorPreferenceRepository preferences, OperatorRepository operators, SiteSettingRepository siteSettings, AuditLogService audit) {
        this.preferences = preferences;
        this.operators = operators;
        this.siteSettings = siteSettings;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public MyDisplayResponse get(AdminActor actor) {
        return view(preferences.findById(actor.operatorId()).orElse(null), inherited());
    }

    @Transactional
    public MyDisplayResponse save(AdminActor actor, MyDisplayRequest request) {
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        if (request == null || request.use() == null) {
            errors.put("use", new MessageRef("profile.display.use"));
        }
        DisplayValuesPolicy.Checked values = null;
        try {
            values = DisplayValuesPolicy.check(request == null ? null : request.values());
        } catch (ValidationException e) {
            errors.putAll(e.getFields());
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        // 같은 운영자의 설정 행을 동시에 만드는 두 요청을 줄 세운다
        operators.findWithLockById(actor.operatorId()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        Instant now = Instant.now();
        OperatorPreferenceEntity preference = preferences.findById(actor.operatorId()).orElse(null);
        boolean use = request.use();
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        boolean hadValues = preference != null && preference.hasDisplayValues();
        boolean hadUse = preference != null && preference.isDisplayUse();
        if (hadUse != use) {
            before.put("use", hadUse);
            after.put("use", use);
        }
        diff(before, after, "language", hadValues ? preference.getDisplayLanguage() : null, values.language());
        diff(before, after, "zoneId", hadValues ? preference.getDisplayZoneId() : null, values.zoneId());
        diff(before, after, "timeFormat", hadValues ? preference.getDisplayTimeFormat() : null, values.timeFormat());
        diff(before, after, "dateFormat", hadValues ? preference.getDisplayDateFormat() : null, values.dateFormat());

        if (preference == null) {
            preference = OperatorPreferenceEntity.create(actor.operatorId(), now);
            preferences.persist(preference);
        }
        if (!after.isEmpty() || !hadValues) {
            preference.changeDisplay(use, values.language(), values.zoneId(), values.timeFormat(), values.dateFormat(), now);
        }
        if (!after.isEmpty()) {
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.PROFILE_DISPLAY_UPDATE, AuditResult.SUCCESS)
                    .target("OPERATOR", Long.toString(actor.operatorId())).with("before", before).with("after", after));
            log.info("내 표시 형식을 바꿨다 operatorId={} use={}", actor.operatorId(), use);
        }
        return view(preference, inherited());
    }

    /** 바뀐 칸만 전후를 담는다 — 처음 저장이면(전 값 없음) 후 값만 */
    private static void diff(Map<String, Object> before, Map<String, Object> after, String field, String was, String now) {
        if (!now.equals(was)) {
            if (was != null) {
                before.put(field, was);
            }
            after.put(field, now);
        }
    }

    private DisplayValuesView inherited() {
        Optional<SiteSettingEntity> row = siteSettings.findById(SiteSettingEntity.ROW_ID);
        return row.map(r -> new DisplayValuesView(r.getDisplayLanguage(), r.getDisplayZoneId(), r.getDisplayTimeFormat(), r.getDisplayDateFormat()))
                .orElseGet(() -> new DisplayValuesView(SiteSettingEntity.DEFAULT_LANGUAGE, SiteSettingEntity.DEFAULT_ZONE_ID, SiteSettingEntity.DEFAULT_TIME_FORMAT,
                        SiteSettingEntity.DEFAULT_DATE_FORMAT));
    }

    private static MyDisplayResponse view(OperatorPreferenceEntity preference, DisplayValuesView inherited) {
        boolean use = preference != null && preference.isDisplayUse();
        DisplayValuesView values = preference != null && preference.hasDisplayValues()
                ? new DisplayValuesView(preference.getDisplayLanguage(), preference.getDisplayZoneId(), preference.getDisplayTimeFormat(), preference.getDisplayDateFormat())
                : inherited;
        return new MyDisplayResponse(use, values, inherited, use ? "USER" : "SYSTEM");
    }
}
