package com.community.site.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.permission.dto.AdminActor;
import com.community.site.dto.request.SiteBasicRequest;
import com.community.site.dto.response.SiteBasicResponse;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이트 기본 정보(이름·한 줄 소개) 조회·저장 (docs/features/site-settings-contract.md). 공개 주소({@code publicUrl})는 이 서비스가 건드리지 않는다
 * (소셜 로그인 콜백·메일 링크의 기준이라 변경 정책이 먼저다). 로고는 따로({@link SiteLogoService}) 즉시 적용된다 — 여기서는 지금 로고 주소를 응답에 실어 줄 뿐이다.
 *
 * <p>검사는 설치 마법사와 같은 규칙({@code SitePolicy})을 쓰되 문구는 화면 문구와 같다: 이름 1~50자(앞뒤 공백을 자른 뒤)·꺾쇠·제어 문자 없음 → {@code fields.siteName},
 * 한 줄 소개 0~100자·꺾쇠·제어 문자 없음(비우면 문구 없음) → {@code fields.tagline}. 두 칸의 위반을 한꺼번에 돌려준다.
 */
@Service
public class SiteBasicService {

    private static final Logger log = LoggerFactory.getLogger(SiteBasicService.class);

    private final SiteSettingRepository settings;
    private final SiteAssets assets;
    private final AuditLogService audit;

    public SiteBasicService(SiteSettingRepository settings, SiteAssets assets, AuditLogService audit) {
        this.settings = settings;
        this.assets = assets;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public SiteBasicResponse load() {
        SiteSettingEntity row = settings.findById(SiteSettingEntity.ROW_ID).orElseThrow(() -> new CommonException(ErrorCode.NOT_FOUND));
        return view(row);
    }

    @Transactional
    public SiteBasicResponse save(AdminActor actor, SiteBasicRequest request) {
        String name = SitePolicy.normalizeName(request == null ? null : request.siteName());
        String tagline = SitePolicy.normalizeTagline(request == null ? null : request.tagline());
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        SitePolicy.nameViolation(name).ifPresent(violation -> errors.put("siteName", nameMessage(violation)));
        SitePolicy.taglineViolation(tagline).ifPresent(violation -> errors.put("tagline", taglineMessage(violation)));
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        SiteSettingEntity row = settings.findWithLockById(SiteSettingEntity.ROW_ID)
                .orElseThrow(() -> ValidationException.of("site", new MessageRef("siteSetting.missing")));
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(row.getSiteName(), name)) {
            changed.add("siteName");
        }
        if (!Objects.equals(row.getTagline(), tagline)) {
            changed.add("tagline");
        }
        if (!changed.isEmpty()) {
            String oldName = row.getSiteName();
            String oldTagline = row.getTagline();
            row.changeBasic(name, tagline, actor.operatorId(), Instant.now());
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_BASIC_UPDATE, AuditResult.SUCCESS).target("SITE", "1")
                    .with("changed", changed).with("siteName", oldName + " -> " + name).with("tagline", (oldTagline == null ? "" : oldTagline) + " -> " + (tagline == null ? "" : tagline)));
            log.info("사이트 기본 정보를 바꿨다 changed={} operatorId={}", changed, actor.operatorId());
        }
        return view(row);
    }

    private SiteBasicResponse view(SiteSettingEntity row) {
        return new SiteBasicResponse(row.getSiteName(), row.getTagline(), assets.logoUrl(row).orElse(null));
    }

    /** {@link SitePolicy} 의 위반 키를 화면 문구와 같은 말의 키로 바꾼다(길이는 한도를 싣고, 꺾쇠·제어 문자는 한 문구다) */
    private static MessageRef nameMessage(MessageRef violation) {
        return switch (violation.key()) {
            case "site.name.required" -> new MessageRef("siteSetting.name.required");
            case "site.name.length" -> new MessageRef("siteSetting.name.length", String.valueOf(SitePolicy.NAME_MAX_LENGTH));
            default -> new MessageRef("siteSetting.name.chars");
        };
    }

    private static MessageRef taglineMessage(MessageRef violation) {
        if ("site.tagline.length".equals(violation.key())) {
            return new MessageRef("siteSetting.tagline.length", String.valueOf(SitePolicy.TAGLINE_MAX_LENGTH));
        }
        return new MessageRef("siteSetting.tagline.chars");
    }
}
