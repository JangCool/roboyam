package com.community.site.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.media.entity.FileEntity;
import com.community.media.entity.FilePurpose;
import com.community.media.entity.FileStatus;
import com.community.media.repository.master.FileRepository;
import com.community.notification.service.AdminNotices;
import com.community.permission.dto.AdminActor;
import com.community.site.dto.request.SiteLogoBundleRequest;
import com.community.site.dto.request.SiteLogoNameInLogoRequest;
import com.community.site.dto.request.SiteLogoRulesRequest;
import com.community.site.dto.response.SiteLogoLibraryResponse;
import com.community.site.entity.SiteLogoBundleEntity;
import com.community.site.entity.SiteLogoRuleEntity;
import com.community.site.entity.SiteLogoVariant;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.exception.SiteLogoBundleNotEditableException;
import com.community.site.exception.SiteLogoBundleNotFoundException;
import com.community.site.repository.master.SiteLogoBundleRepository;
import com.community.site.repository.master.SiteLogoRuleRepository;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 로고 보관함(묶음)과 보이기 규칙 — V249, docs/features/site-logo-library-contract.md. 응답은 늘 묶음 목록 + 규칙 전체다.
 *
 * <ul>
 *   <li><b>묶음</b>({@link #createBundle}·{@link #updateBundle}): 이름(앞뒤 공백을 뗀 1~{@value SiteLogoBundleEntity#NAME_MAX}자) · 「이름 포함」 · 4칸. 칸마다
 *       그림 파일(먼저 {@code POST /api/admin/files} 로 올린 {@code PENDING} 파일 — 지정하면 {@code IN_USE}, 파일 하나는 한 칸만 가리킨다) · SVG 소스
 *       ({@link SvgInspector} — 위험 요소가 있으면 정화하지 않고 거절) · 그대로({@code "KEEP"}·칸 생략) · 비움({@code null}). 저장한 뒤 <b>한 칸 이상</b> 채워져 있어야 한다.
 *       칸에서 빠진 그림 파일은 장부에서 지우고 저장소 파일은 커밋 뒤에 지운다. 묶음은 지우지 못한다(보관). 기본 제공 묶음은 고치지 못한다(409).</li>
 *   <li><b>규칙</b>({@link #saveRules}): 평소 로고 · 기념일 줄(차례 = 우선순위, 줄 전체를 갈아 끼운다) · 성별(남성·여성 묶음 또는 고르지 않음) ·
 *       기념일 로고 유형({@code holidayStyle} — AUTO·CHARACTER·WORDMARK, 없으면 그대로). 가리키는 묶음이 모두 있어야 한다.</li>
 *   <li>검사 위반은 한꺼번에 {@code 400 VALIDATION} — 칸 경로 {@code name} · {@code cells} · {@code cells.koLight} … · {@code defaultBundleId} ·
 *       {@code anniversaries[0].name|mode|startDate|endDate|holidayName|bundleId} · {@code gender.male|female} · {@code holidayStyle}.</li>
 * </ul>
 * 사이트 설정 행(규칙)·묶음 행(고치기)을 쓰기 잠금으로 잡아 동시에 바꾸는 요청을 줄 세운다. 바뀐 일은 감사 기록({@code SITE_LOGO_BUNDLE_CREATE}·{@code SITE_LOGO_BUNDLE_UPDATE}·
 * {@code SITE_LOGO_RULES_UPDATE})에 남긴다 — 파일 이름·내용·SVG 소스는 남기지 않는다. 감사 기록이 남으면 {@code CacheAutoRefresh} 가 공개 사본({@code site.info})을 비운다.
 */
@Service
public class SiteLogoLibraryService {

    private static final Logger log = LoggerFactory.getLogger(SiteLogoLibraryService.class);

    /** 기념일 줄 수 한도 */
    static final int RULES_MAX = 50;
    private static final String KEEP = "KEEP";

    private final SiteSettingRepository settings;
    private final SiteLogoBundleRepository bundles;
    private final SiteLogoRuleRepository rules;
    private final FileRepository files;
    private final SiteAssets assets;
    private final SiteImageFiles imageFiles;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;
    private final AdminNotices notices;
    private final TransactionTemplate tx;

    public SiteLogoLibraryService(SiteSettingRepository settings, SiteLogoBundleRepository bundles, SiteLogoRuleRepository rules, FileRepository files, SiteAssets assets,
                                  SiteImageFiles imageFiles, SnowflakeIdGenerator ids, AuditLogService audit, AdminNotices notices, PlatformTransactionManager txManager) {
        this.settings = settings;
        this.bundles = bundles;
        this.rules = rules;
        this.files = files;
        this.assets = assets;
        this.imageFiles = imageFiles;
        this.ids = ids;
        this.audit = audit;
        this.notices = notices;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 한 칸에 넣을 것 */
    private sealed interface Cell {
    }

    /** 지금 그대로 */
    private record Keep() implements Cell {
    }

    /** 비움 */
    private record Clear() implements Cell {
    }

    private record FileCell(long fileId) implements Cell {
    }

    /** 검사를 통과한 SVG 소스 */
    private record SvgCell(String source) implements Cell {
    }

    /** 바뀐 뒤의 보관함과, 커밋 뒤에 지울 저장소 파일의 키 */
    private record Changed(SiteLogoLibraryResponse library, List<String> oldKeys) {
    }

    // ── 읽기 ──

    /** 보관함과 규칙 — 설정 행이 없으면(설치 전) {@code fields.site} */
    @Transactional(readOnly = true)
    public SiteLogoLibraryResponse read() {
        return library(settings.findById(SiteSettingEntity.ROW_ID).orElseThrow(SiteLogoLibraryService::missing));
    }

    // ── 묶음 ──

    /** 새 묶음을 올린다 — {@code "KEEP"}·뺀 칸은 빈 칸이다 */
    public SiteLogoLibraryResponse createBundle(AdminActor actor, SiteLogoBundleRequest body) {
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        String name = nameOf(body, errors);
        Map<SiteLogoVariant, Cell> cells = cellsOf(body, errors);
        String requestedStyle = styleOf(body, errors);
        if (errors.isEmpty() && cells.values().stream().noneMatch(cell -> cell instanceof FileCell || cell instanceof SvgCell)) {
            errors.put("cells", new MessageRef("siteLogo.bundle.cells.required"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        boolean nameInLogo = Boolean.TRUE.equals(body.nameInLogo());
        // 유형을 안 보내면 캐릭터 — 고른 값으로 적어 둔다(null 과 뜻이 같다)
        String style = requestedStyle == null ? SiteLogoBundleEntity.STYLE_CHARACTER : requestedStyle;
        Changed changed = tx.execute(status -> {
            SiteSettingEntity row = settings.findById(SiteSettingEntity.ROW_ID).orElseThrow(SiteLogoLibraryService::missing);
            Instant now = Instant.now();
            SiteLogoBundleEntity bundle = SiteLogoBundleEntity.create(ids.nextId(), name, nameInLogo, actor.operatorId(), now);
            bundle.changeStyle(style, now);
            List<String> changedCells = new ArrayList<>();
            apply(bundle, cells, now, changedCells);
            bundles.persistAndFlush(bundle);
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_LOGO_BUNDLE_CREATE, AuditResult.SUCCESS).target("SITE", "1")
                    .with("bundleId", Long.toString(bundle.getId())).with("name", name).with("cells", changedCells).with("nameInLogo", nameInLogo)
                    .with("style", style));
            notices.siteSettingsChanged(actor, "siteLogo");
            log.info("로고 묶음을 올렸다 operatorId={} bundleId={} cells={}", actor.operatorId(), bundle.getId(), changedCells);
            return new Changed(library(row), List.of());
        });
        return changed.library();
    }

    /** 올린 묶음을 고친다 — 없는 묶음 404, 기본 제공 묶음 409 */
    public SiteLogoLibraryResponse updateBundle(AdminActor actor, String idText, SiteLogoBundleRequest body) {
        long id = parseId(idText).orElseThrow(SiteLogoBundleNotFoundException::new);
        // 입력 검사(SVG 읽기)보다 먼저 — 기본 제공 묶음은 본문과 무관하게 409 다. 저장할 때 잠근 뒤 다시 본다
        requireEditable(bundles.findById(id).orElseThrow(SiteLogoBundleNotFoundException::new));

        Map<String, MessageRef> errors = new LinkedHashMap<>();
        String name = nameOf(body, errors);
        Map<SiteLogoVariant, Cell> cells = cellsOf(body, errors);
        String requestedStyle = styleOf(body, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        boolean nameInLogo = Boolean.TRUE.equals(body.nameInLogo());
        Changed changed = tx.execute(status -> {
            SiteSettingEntity row = settings.findById(SiteSettingEntity.ROW_ID).orElseThrow(SiteLogoLibraryService::missing);
            SiteLogoBundleEntity bundle = bundles.findWithLockById(id).orElseThrow(SiteLogoBundleNotFoundException::new);
            requireEditable(bundle);
            Instant now = Instant.now();
            String oldName = bundle.getName();
            boolean oldNameInLogo = bundle.isNameInLogo();
            List<String> changedCells = new ArrayList<>();
            List<String> oldKeys = apply(bundle, cells, now, changedCells);
            if (java.util.Arrays.stream(SiteLogoVariant.values()).noneMatch(bundle::filled)) {
                throw ValidationException.of("cells", new MessageRef("siteLogo.bundle.cells.required")); // 트랜잭션째 되돌아간다
            }
            // 유형 — 안 보내면 그대로. 고른 적 없는 묶음(null)에 캐릭터를 보내도 유효 유형이 같으니 바뀐 것으로 치지 않는다
            String oldStyle = bundle.effectiveStyle();
            boolean styleChanged = requestedStyle != null && !requestedStyle.equals(oldStyle);
            boolean metaChanged = !oldName.equals(name) || oldNameInLogo != nameInLogo || styleChanged;
            if (!oldName.equals(name) || oldNameInLogo != nameInLogo) {
                bundle.change(name, nameInLogo, now);
            }
            if (styleChanged) {
                bundle.changeStyle(requestedStyle, now);
            }
            if (metaChanged || !changedCells.isEmpty()) {
                AuditEvent event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_LOGO_BUNDLE_UPDATE, AuditResult.SUCCESS).target("SITE", "1")
                        .with("bundleId", Long.toString(id)).with("cells", changedCells);
                if (!oldName.equals(name)) {
                    event.with("name", oldName + " -> " + name);
                }
                if (oldNameInLogo != nameInLogo) {
                    event.with("nameInLogo", oldNameInLogo + " -> " + nameInLogo);
                }
                if (styleChanged) {
                    event.with("style", oldStyle + " -> " + requestedStyle);
                }
                audit.record(event);
                notices.siteSettingsChanged(actor, "siteLogo");
                log.info("로고 묶음을 고쳤다 operatorId={} bundleId={} cells={}", actor.operatorId(), id, changedCells);
            }
            bundles.flush(); // 칸이 다른 값을 가리킨 뒤에 옛 파일 장부 행이 지워지게(수정이 삭제보다 먼저 나간다)
            return new Changed(library(row), oldKeys);
        });
        imageFiles.deleteQuietly(changed.oldKeys());
        return changed.library();
    }

    /**
     * 묶음의 「로고에 사이트 이름이 들어 있음」만 바꾼다 — 기본 제공 묶음도 된다(그림·이름은 그대로 잠겨 있다, 2026-11-19 — 기본 로고를 쓰는 사이트가 이 표시를 바꿀 길이 없었다).
     * 없는 묶음 404, 값이 없으면 {@code fields.nameInLogo}. 값이 그대로면 아무것도 적지 않는다.
     */
    public SiteLogoLibraryResponse changeNameInLogo(AdminActor actor, String idText, SiteLogoNameInLogoRequest body) {
        long id = parseId(idText).orElseThrow(SiteLogoBundleNotFoundException::new);
        if (body == null || body.nameInLogo() == null) {
            throw ValidationException.of("nameInLogo", new MessageRef("siteLogo.bundle.nameInLogo.required"));
        }
        boolean nameInLogo = body.nameInLogo();
        return tx.execute(status -> {
            SiteSettingEntity row = settings.findById(SiteSettingEntity.ROW_ID).orElseThrow(SiteLogoLibraryService::missing);
            SiteLogoBundleEntity bundle = bundles.findWithLockById(id).orElseThrow(SiteLogoBundleNotFoundException::new);
            boolean old = bundle.isNameInLogo();
            if (old != nameInLogo) {
                bundle.changeNameInLogo(nameInLogo, Instant.now());
                audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_LOGO_BUNDLE_UPDATE, AuditResult.SUCCESS).target("SITE", "1")
                        .with("bundleId", Long.toString(id)).with("nameInLogo", old + " -> " + nameInLogo));
                notices.siteSettingsChanged(actor, "siteLogo");
                log.info("로고 묶음의 이름 포함을 바꿨다 operatorId={} bundleId={} nameInLogo={}", actor.operatorId(), id, nameInLogo);
                bundles.flush();
            }
            return library(row);
        });
    }

    // ── 규칙 ──

    /** 보이기 규칙을 통째로 저장한다 — 기념일 줄은 전체를 갈아 끼운다(줄 ID 는 새로 매긴다) */
    @Transactional
    public SiteLogoLibraryResponse saveRules(AdminActor actor, SiteLogoRulesRequest body) {
        SiteSettingEntity row = settings.findWithLockById(SiteSettingEntity.ROW_ID).orElseThrow(SiteLogoLibraryService::missing);
        Set<Long> known = bundles.findAllByOrderByIdAsc().stream().map(SiteLogoBundleEntity::getId).collect(Collectors.toSet());
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        Instant now = Instant.now();

        Long defaultId = body == null ? null : parseId(body.defaultBundleId()).filter(known::contains).orElse(null);
        if (defaultId == null) {
            errors.put("defaultBundleId", new MessageRef("siteLogo.rules.default.required"));
        }
        List<SiteLogoRulesRequest.Anniversary> lines = body == null || body.anniversaries() == null ? List.of() : body.anniversaries();
        List<SiteLogoRuleEntity> next = new ArrayList<>();
        if (lines.size() > RULES_MAX) {
            errors.put("anniversaries", new MessageRef("siteLogo.rules.tooMany", RULES_MAX));
        } else {
            for (int i = 0; i < lines.size(); i++) {
                ruleOf(lines.get(i), i, known, errors, now).ifPresent(next::add);
            }
        }
        SiteLogoRulesRequest.Gender gender = body == null ? null : body.gender();
        Long male = genderBundle(gender == null ? null : gender.male(), "gender.male", known, errors);
        Long female = genderBundle(gender == null ? null : gender.female(), "gender.female", known, errors);
        // 기념일 로고 유형 — 보내지 않으면(null) 지금 값 그대로
        String oldHolidayStyle = row.getHolidayLogoStyle();
        String holidayStyle = body == null || body.holidayStyle() == null ? oldHolidayStyle : body.holidayStyle();
        if (!SiteSettingEntity.HOLIDAY_STYLES.contains(holidayStyle)) {
            errors.put("holidayStyle", new MessageRef("siteLogo.rules.holidayStyle.invalid"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        long oldDefault = row.getLogoDefaultBundleId();
        rules.deleteAllInBatch();
        next.forEach(rules::persist);
        row.changeHolidayLogoStyle(holidayStyle);
        row.changeLogoRules(defaultId, male, female, actor.operatorId(), now);
        rules.flush();
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_LOGO_RULES_UPDATE, AuditResult.SUCCESS).target("SITE", "1")
                .with("defaultBundleId", oldDefault + " -> " + defaultId).with("anniversaries", next.size())
                .with("male", male == null ? "" : Long.toString(male)).with("female", female == null ? "" : Long.toString(female))
                .with("holidayStyle", oldHolidayStyle.equals(holidayStyle) ? holidayStyle : oldHolidayStyle + " -> " + holidayStyle));
        notices.siteSettingsChanged(actor, "siteLogo");
        log.info("로고 보이기 규칙을 저장했다 operatorId={} default={} anniversaries={}", actor.operatorId(), defaultId, next.size());
        return library(row);
    }

    // ── 안쪽: 묶음 입력 ──

    private static String nameOf(SiteLogoBundleRequest body, Map<String, MessageRef> errors) {
        String name = body == null || body.name() == null ? "" : body.name().strip();
        if (name.isEmpty()) {
            errors.put("name", new MessageRef("siteLogo.bundle.name.required"));
        } else if (name.codePointCount(0, name.length()) > SiteLogoBundleEntity.NAME_MAX) {
            errors.put("name", new MessageRef("siteLogo.bundle.name.length", SiteLogoBundleEntity.NAME_MAX));
        }
        return name;
    }

    /** 요청의 유형 — 안 보냈으면 null(만들기는 캐릭터·고치기는 그대로), 허용 값 밖이면 {@code style} 에 문구를 모은다 */
    private static String styleOf(SiteLogoBundleRequest body, Map<String, MessageRef> errors) {
        String style = body == null ? null : body.style();
        if (style != null && !SiteLogoBundleEntity.STYLE_CHARACTER.equals(style) && !SiteLogoBundleEntity.STYLE_WORDMARK.equals(style)) {
            errors.put("style", new MessageRef("siteLogo.bundle.style.invalid"));
            return null;
        }
        return style;
    }

    /** 요청의 칸을 읽는다 — 모양이 틀리거나 SVG 검사에 걸리면 {@code cells.<칸>} 에 문구를 모은다. 뺀 칸은 「그대로」 */
    private static Map<SiteLogoVariant, Cell> cellsOf(SiteLogoBundleRequest body, Map<String, MessageRef> errors) {
        Map<SiteLogoVariant, Cell> cells = new EnumMap<>(SiteLogoVariant.class);
        Map<String, JsonNode> raw = body == null || body.cells() == null ? Map.of() : body.cells();
        for (SiteLogoVariant variant : SiteLogoVariant.values()) {
            String field = "cells." + variant.key();
            if (!raw.containsKey(variant.key())) {
                cells.put(variant, new Keep());
                continue;
            }
            JsonNode node = raw.get(variant.key());
            if (node == null || node.isNull()) {
                cells.put(variant, new Clear());
            } else if (node.isString() && KEEP.equals(node.asString())) {
                cells.put(variant, new Keep());
            } else if (node.isObject() && node.get("svgSource") != null && node.get("svgSource").isString()) {
                try {
                    cells.put(variant, new SvgCell(SvgInspector.inspect(node.get("svgSource").asString(), false).source()));
                } catch (ValidationException e) {
                    // 검사기는 칸 이름을 svgSource 로 준다 — 이 요청의 칸 경로로 옮긴다
                    errors.put(field, e.getFields().values().stream().findFirst().orElse(new MessageRef("siteLogo.bundle.cell.invalid")));
                }
            } else if (node.isObject() && node.get("fileId") != null && (node.get("fileId").isString() || node.get("fileId").isIntegralNumber())) {
                Optional<Long> fileId = parseId(node.get("fileId").asString());
                if (fileId.isPresent()) {
                    cells.put(variant, new FileCell(fileId.get()));
                } else {
                    errors.put(field, new MessageRef("siteSetting.logo.fileNotFound"));
                }
            } else {
                errors.put(field, new MessageRef("siteLogo.bundle.cell.invalid"));
            }
        }
        return cells;
    }

    /**
     * 칸을 묶음에 넣는다(트랜잭션 안). 그림 파일은 올려 둔 것({@code PENDING}, 용도 SITE_LOGO)만 — 이미 그 칸의 파일이면 그대로 둔다. 다른 칸·다른 묶음이 쓰는 파일은 거절한다.
     *
     * @param changedCells 실제로 바뀐 칸 이름을 담는다(감사 기록용)
     * @return 칸에서 빠져 커밋 뒤에 지울 저장소 파일의 키
     */
    private List<String> apply(SiteLogoBundleEntity bundle, Map<SiteLogoVariant, Cell> cells, Instant now, List<String> changedCells) {
        List<String> oldKeys = new ArrayList<>();
        cells.forEach((variant, cell) -> {
            switch (cell) {
                case Keep keep -> {
                }
                case Clear clear -> {
                    if (bundle.filled(variant)) {
                        retireFile(bundle, variant, oldKeys);
                        bundle.clear(variant, now);
                        changedCells.add(variant.key());
                    }
                }
                case FileCell fileCell -> {
                    Long currentId = bundle.fileId(variant);
                    if (currentId != null && currentId == fileCell.fileId()) {
                        return; // 이미 이 파일이 이 칸의 그림이다
                    }
                    FileEntity file = files.findWithLockById(fileCell.fileId()).filter(f -> f.getPurpose() == FilePurpose.SITE_LOGO)
                            .filter(f -> f.getStatus() == FileStatus.PENDING)
                            .orElseThrow(() -> ValidationException.of("cells." + variant.key(), new MessageRef("siteSetting.logo.fileNotFound")));
                    retireFile(bundle, variant, oldKeys);
                    bundle.putFile(variant, fileCell.fileId(), now);
                    file.markUsed(now);
                    changedCells.add(variant.key());
                }
                case SvgCell svgCell -> {
                    if (svgCell.source().equals(bundle.svg(variant))) {
                        return;
                    }
                    retireFile(bundle, variant, oldKeys);
                    bundle.putSvg(variant, svgCell.source(), now);
                    changedCells.add(variant.key());
                }
            }
        });
        return oldKeys;
    }

    /** 그 칸의 지금 그림이 파일이면 장부 행을 지우고 저장소 키를 모은다 — 부르는 쪽이 이어서 칸을 다른 값으로 바꾼다 */
    private void retireFile(SiteLogoBundleEntity bundle, SiteLogoVariant variant, List<String> oldKeys) {
        Long oldId = bundle.fileId(variant);
        if (oldId == null) {
            return;
        }
        files.findById(oldId).ifPresent(old -> {
            oldKeys.add(old.getStorageKey());
            files.delete(old);
        });
    }

    private static void requireEditable(SiteLogoBundleEntity bundle) {
        if (bundle.isBuiltIn()) {
            throw new SiteLogoBundleNotEditableException();
        }
    }

    // ── 안쪽: 규칙 입력 ──

    /** 기념일 한 줄을 검사해 행으로 만든다 — 위반이 있으면 {@code anniversaries[i].…} 에 모으고 비어 있다 */
    private Optional<SiteLogoRuleEntity> ruleOf(SiteLogoRulesRequest.Anniversary line, int index, Set<Long> known, Map<String, MessageRef> errors, Instant now) {
        String path = "anniversaries[" + index + "].";
        int before = errors.size();
        String name = line == null || line.name() == null ? "" : line.name().strip();
        if (name.isEmpty()) {
            errors.put(path + "name", new MessageRef("siteLogo.rules.name.required"));
        } else if (name.codePointCount(0, name.length()) > SiteLogoRuleEntity.NAME_MAX) {
            errors.put(path + "name", new MessageRef("siteLogo.rules.name.length", SiteLogoRuleEntity.NAME_MAX));
        }
        String mode = line == null ? null : line.mode();
        boolean yearly = line != null && Boolean.TRUE.equals(line.yearly());
        String start = line == null ? null : blankToNull(line.startDate());
        String end = line == null ? null : blankToNull(line.endDate());
        String holidayName = line == null ? null : blankToNull(line.holidayName());
        if (SiteLogoRuleEntity.MODE_DATES.equals(mode)) {
            if (start == null || end == null) {
                errors.put(path + (start == null ? "startDate" : "endDate"), new MessageRef("siteLogo.rules.dates.required"));
            } else if (yearly) {
                if (!SiteLogoRules.monthDay(start)) {
                    errors.put(path + "startDate", new MessageRef("siteLogo.rules.dates.invalid"));
                }
                if (!SiteLogoRules.monthDay(end)) {
                    errors.put(path + "endDate", new MessageRef("siteLogo.rules.dates.invalid"));
                }
            } else {
                Optional<LocalDate> from = SiteLogoRules.date(start);
                Optional<LocalDate> to = SiteLogoRules.date(end);
                if (from.isEmpty()) {
                    errors.put(path + "startDate", new MessageRef("siteLogo.rules.dates.invalid"));
                }
                if (to.isEmpty()) {
                    errors.put(path + "endDate", new MessageRef("siteLogo.rules.dates.invalid"));
                }
                if (from.isPresent() && to.isPresent() && to.get().isBefore(from.get())) {
                    errors.put(path + "endDate", new MessageRef("siteLogo.rules.dates.order"));
                }
            }
        } else if (SiteLogoRuleEntity.MODE_HOLIDAY.equals(mode)) {
            if (holidayName == null) {
                errors.put(path + "holidayName", new MessageRef("siteLogo.rules.holiday.required"));
            } else if (holidayName.codePointCount(0, holidayName.length()) > SiteLogoRuleEntity.HOLIDAY_NAME_MAX) {
                errors.put(path + "holidayName", new MessageRef("siteLogo.rules.holiday.length", SiteLogoRuleEntity.HOLIDAY_NAME_MAX));
            }
        } else {
            errors.put(path + "mode", new MessageRef("siteLogo.rules.mode.invalid"));
        }
        String bundleText = line == null ? null : blankToNull(line.bundleId());
        Long bundleId = parseId(bundleText).filter(known::contains).orElse(null);
        if (bundleId == null) {
            errors.put(path + "bundleId", new MessageRef(bundleText == null ? "siteLogo.rules.bundle.required" : "siteLogo.rules.bundle.notFound"));
        }
        if (errors.size() != before) {
            return Optional.empty();
        }
        boolean enabled = line.enabled() == null || line.enabled();
        return Optional.of(SiteLogoRuleEntity.MODE_DATES.equals(mode)
                ? SiteLogoRuleEntity.dates(ids.nextId(), index, name, enabled, start, end, yearly, bundleId, now)
                : SiteLogoRuleEntity.holiday(ids.nextId(), index, name, enabled, holidayName, bundleId, now));
    }

    /** 성별 칸 — 비었으면 고르지 않음(null), 값이 있으면 있는 묶음이어야 한다 */
    private static Long genderBundle(String text, String field, Set<Long> known, Map<String, MessageRef> errors) {
        String value = blankToNull(text);
        if (value == null) {
            return null;
        }
        Long id = parseId(value).filter(known::contains).orElse(null);
        if (id == null) {
            errors.put(field, new MessageRef("siteLogo.rules.bundle.notFound"));
        }
        return id;
    }

    // ── 안쪽: 응답 ──

    /** 보관함 전체 — 지금 트랜잭션 안에서 부르면 방금 바꾼 값이 보인다 */
    private SiteLogoLibraryResponse library(SiteSettingEntity row) {
        List<SiteLogoLibraryResponse.Bundle> bundleViews = bundles.findAllByOrderByIdAsc().stream()
                .map(b -> new SiteLogoLibraryResponse.Bundle(Long.toString(b.getId()), b.getName(), b.isBuiltIn(), b.isBuiltIn() ? null : b.getCreatedAt(),
                        assets.logoUrls(b), b.isNameInLogo(), b.effectiveStyle(), b.getDayKey()))
                .toList();
        List<SiteLogoLibraryResponse.Anniversary> anniversaries = rules.findAllByOrderBySortOrderAsc().stream()
                .map(r -> new SiteLogoLibraryResponse.Anniversary(Long.toString(r.getId()), r.getName(), r.isEnabled(), r.getMode(), r.getStartDate(), r.getEndDate(),
                        r.isYearly(), r.getHolidayName(), Long.toString(r.getBundleId())))
                .toList();
        return new SiteLogoLibraryResponse(bundleViews, new SiteLogoLibraryResponse.Rules(Long.toString(row.getLogoDefaultBundleId()), anniversaries,
                new SiteLogoLibraryResponse.Gender(idText(row.getLogoMaleBundleId()), idText(row.getLogoFemaleBundleId())), row.getHolidayLogoStyle()));
    }

    private static String idText(Long id) {
        return id == null ? null : Long.toString(id);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static Optional<Long> parseId(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(text.strip()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static ValidationException missing() {
        return ValidationException.of("site", new MessageRef("siteSetting.missing"));
    }
}
