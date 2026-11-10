package com.community.osslicense.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.osslicense.dto.OssNoticeEntryFields;
import com.community.osslicense.dto.request.OssNoticeEntrySaveRequest;
import com.community.osslicense.dto.request.OssNoticeHiddenRequest;
import com.community.osslicense.dto.request.OssNoticeIntroRequest;
import com.community.osslicense.dto.response.OssNoticeAdminView;
import com.community.osslicense.dto.response.OssNoticeEntryView;
import com.community.osslicense.entity.OssNoticeEntryEntity;
import com.community.osslicense.entity.OssNoticeSettingEntity;
import com.community.osslicense.exception.OssNoticeEntryNotFoundException;
import com.community.osslicense.repository.master.OssNoticeEntryRepository;
import com.community.osslicense.repository.master.OssNoticeEntryRepositoryQDSL;
import com.community.osslicense.repository.master.OssNoticeSettingRepository;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오픈소스 라이선스 고지 관리(docs/features/oss-licenses-contract.md 「관리자」) — 보기는 {@code site.read}, 바꾸기는 {@code site.manage}(컨트롤러가 선언). 칸 규칙은 {@link OssNotices}.
 *
 * <ul>
 *   <li><b>안내 문구</b>: 한 줄 표({@code oss_notice_settings})를 잠그고 바꾼다. 비우면 null(공개 화면 기본 문구). 바뀐 때만 감사 {@code OSS_NOTICE_INTRO_UPDATE}.</li>
 *   <li><b>직접 추가 항목</b>: 칸 검사는 모아서 {@code 400 VALIDATION}({@code fields.name|version|license|copyright|licenseText|sourceUrl|category}). 목록은 이름순.
 *       감사는 만들기·고치기(바뀐 때만)·숨기기(바뀐 때만)·지우기 — 전문은 길어 글자 수만 싣는다.</li>
 *   <li>공개 사이트(api)의 사본(캐시 {@code oss.notice})은 감사 기록을 보고 {@code CacheAutoRefresh} 가 같은 트랜잭션에서 비운다.</li>
 * </ul>
 */
@Service
public class OssNoticeAdminService {

    private static final Logger log = LoggerFactory.getLogger(OssNoticeAdminService.class);
    private static final String TARGET_TYPE = "OSS_NOTICE";

    private final OssNoticeSettingRepository settings;
    private final OssNoticeEntryRepository entries;
    private final OssNoticeEntryRepositoryQDSL entryQueries;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;

    public OssNoticeAdminService(OssNoticeSettingRepository settings, OssNoticeEntryRepository entries, OssNoticeEntryRepositoryQDSL entryQueries,
                                 AuditLogService audit, SnowflakeIdGenerator ids) {
        this.settings = settings;
        this.entries = entries;
        this.entryQueries = entryQueries;
        this.audit = audit;
        this.ids = ids;
    }

    // ── 읽기 ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OssNoticeAdminView get() {
        OssNoticeSettingEntity row = settings.findById(OssNoticeSettingEntity.ROW_ID).orElse(null);
        return view(row);
    }

    // ── 쓰기 ──────────────────────────────────────────────────────────────

    @Transactional
    public OssNoticeAdminView updateIntro(AdminActor actor, OssNoticeIntroRequest body) {
        String intro = OssNotices.multiline(body == null ? null : body.intro());
        OssNotices.tooLong("intro", intro, OssNotices.INTRO_MAX).ifPresent(v -> {
            throw ValidationException.of("intro", v);
        });
        OssNoticeSettingEntity row = settings.findWithLockById(OssNoticeSettingEntity.ROW_ID)
                .orElseThrow(() -> ValidationException.of("intro", new MessageRef("ossNotice.settings.missing")));
        String before = row.getIntro();
        if (!Objects.equals(before, intro)) {
            row.changeIntro(intro, actor.operatorId(), Instant.now());
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.OSS_NOTICE_INTRO_UPDATE, AuditResult.SUCCESS)
                    .target(TARGET_TYPE, "intro").with("from", before).with("to", intro));
            log.info("오픈소스 라이선스 안내 문구를 바꿨다 by operatorId={}", actor.operatorId());
        }
        return view(row);
    }

    @Transactional
    public OssNoticeEntryView create(AdminActor actor, OssNoticeEntrySaveRequest body) {
        OssNoticeEntryFields f = check(body == null ? OssNoticeEntrySaveRequest.empty() : body, false);
        OssNoticeEntryEntity row = OssNoticeEntryEntity.create(ids.nextId(), f, actor.operatorId(), Instant.now());
        entries.persist(row);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.OSS_NOTICE_ENTRY_CREATE, AuditResult.SUCCESS)
                .target(TARGET_TYPE, Long.toString(row.getId())).with("after", snapshot(row)));
        log.info("오픈소스 라이선스 항목을 더했다 id={} by operatorId={}", row.getId(), actor.operatorId());
        return entryView(row);
    }

    @Transactional
    public OssNoticeEntryView update(AdminActor actor, String idText, OssNoticeEntrySaveRequest body) {
        OssNoticeEntryEntity row = requireLocked(idText);
        OssNoticeEntrySaveRequest b = body == null ? OssNoticeEntrySaveRequest.empty() : body;
        OssNoticeEntryFields f = check(b, row.isHidden());
        Map<String, Object> before = snapshot(row);
        row.change(f, actor.operatorId(), Instant.now());
        Map<String, Object> after = snapshot(row);
        Map<String, Object> changedBefore = new LinkedHashMap<>();
        Map<String, Object> changedAfter = new LinkedHashMap<>();
        after.forEach((k, v) -> {
            if (!Objects.equals(before.get(k), v)) {
                changedBefore.put(k, before.get(k));
                changedAfter.put(k, v);
            }
        });
        if (!changedAfter.isEmpty()) {
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.OSS_NOTICE_ENTRY_UPDATE, AuditResult.SUCCESS)
                    .target(TARGET_TYPE, Long.toString(row.getId())).with("before", changedBefore).with("after", changedAfter));
            log.info("오픈소스 라이선스 항목을 고쳤다 id={} 칸={} by operatorId={}", row.getId(), changedAfter.keySet(), actor.operatorId());
        }
        return entryView(row);
    }

    @Transactional
    public OssNoticeEntryView hidden(AdminActor actor, String idText, OssNoticeHiddenRequest body) {
        if (body == null || body.hidden() == null) {
            throw ValidationException.of("hidden", new MessageRef("ossNotice.hidden.required"));
        }
        OssNoticeEntryEntity row = requireLocked(idText);
        boolean before = row.isHidden();
        if (before != body.hidden()) {
            row.changeHidden(body.hidden(), actor.operatorId(), Instant.now());
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.OSS_NOTICE_ENTRY_HIDDEN, AuditResult.SUCCESS)
                    .target(TARGET_TYPE, Long.toString(row.getId())).with("from", before).with("to", body.hidden()));
            log.info("오픈소스 라이선스 항목 숨김을 바꿨다 id={} {} -> {} by operatorId={}", row.getId(), before, body.hidden(), actor.operatorId());
        }
        return entryView(row);
    }

    @Transactional
    public void delete(AdminActor actor, String idText) {
        OssNoticeEntryEntity row = requireLocked(idText);
        Map<String, Object> before = snapshot(row);
        entries.delete(row);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.OSS_NOTICE_ENTRY_DELETE, AuditResult.SUCCESS)
                .target(TARGET_TYPE, Long.toString(row.getId())).with("before", before));
        log.info("오픈소스 라이선스 항목을 지웠다 id={} by operatorId={}", row.getId(), actor.operatorId());
    }

    // ── 도우미 ────────────────────────────────────────────────────────────

    /** 칸 검사·정리 — 문제는 모아 {@code 400 VALIDATION}. 숨김은 비우면 {@code hiddenDefault} */
    private static OssNoticeEntryFields check(OssNoticeEntrySaveRequest b, boolean hiddenDefault) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        String name = OssNotices.line(b.name());
        if (name == null) {
            fields.put("name", new MessageRef("ossNotice.name.required"));
        } else {
            OssNotices.tooLong("name", name, OssNotices.NAME_MAX).ifPresent(v -> fields.put("name", v));
        }
        String version = OssNotices.line(b.version());
        OssNotices.tooLong("version", version, OssNotices.VERSION_MAX).ifPresent(v -> fields.put("version", v));
        String license = OssNotices.line(b.license());
        if (license == null) {
            fields.put("license", new MessageRef("ossNotice.license.required"));
        } else {
            OssNotices.tooLong("license", license, OssNotices.LICENSE_MAX).ifPresent(v -> fields.put("license", v));
        }
        String copyright = OssNotices.multiline(b.copyright());
        OssNotices.tooLong("copyright", copyright, OssNotices.COPYRIGHT_MAX).ifPresent(v -> fields.put("copyright", v));
        String licenseText = OssNotices.multiline(b.licenseText());
        OssNotices.tooLong("licenseText", licenseText, OssNotices.LICENSE_TEXT_MAX).ifPresent(v -> fields.put("licenseText", v));
        String sourceUrl = OssNotices.line(b.sourceUrl());
        OssNotices.sourceUrlViolation(sourceUrl).ifPresent(v -> fields.put("sourceUrl", v));
        String category = OssNotices.category(b.category());
        if (category == null) {
            fields.put("category", new MessageRef("ossNotice.category"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return new OssNoticeEntryFields(name, version, license, copyright, licenseText, sourceUrl, category, b.hidden() == null ? hiddenDefault : b.hidden());
    }

    private OssNoticeEntryEntity requireLocked(String idText) {
        Long id = parseId(idText);
        if (id == null) {
            throw new OssNoticeEntryNotFoundException();
        }
        return entries.findWithLockById(id).orElseThrow(OssNoticeEntryNotFoundException::new);
    }

    private static Long parseId(String raw) {
        if (raw == null || raw.isBlank() || raw.strip().length() > 19) {
            return null;
        }
        try {
            long id = Long.parseLong(raw.strip());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 감사용 칸 값 — 전문은 글자 수만(길고, 바뀌었는지는 글자 수·지문으로 충분하다) */
    private static Map<String, Object> snapshot(OssNoticeEntryEntity row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", row.getName());
        m.put("version", row.getVersion());
        m.put("license", row.getLicense());
        m.put("copyright", row.getCopyright());
        m.put("licenseTextLength", OssNotices.length(row.getLicenseText()));
        m.put("licenseTextHash", row.getLicenseText() == null ? null : Integer.toHexString(row.getLicenseText().hashCode()));
        m.put("sourceUrl", row.getSourceUrl());
        m.put("category", row.getCategory());
        m.put("hidden", row.isHidden());
        return m;
    }

    private OssNoticeAdminView view(OssNoticeSettingEntity row) {
        List<OssNoticeEntryView> list = entryQueries.findAllByName().stream().map(OssNoticeAdminService::entryView).toList();
        return new OssNoticeAdminView(row == null ? null : row.getIntro(), row == null ? null : row.getUpdatedAt(), list);
    }

    private static OssNoticeEntryView entryView(OssNoticeEntryEntity e) {
        return new OssNoticeEntryView(Long.toString(e.getId()), e.getName(), e.getVersion(), e.getLicense(), e.getCopyright(), e.getLicenseText(), e.getSourceUrl(),
                e.getCategory(), e.isHidden(), e.getUpdatedAt());
    }
}
