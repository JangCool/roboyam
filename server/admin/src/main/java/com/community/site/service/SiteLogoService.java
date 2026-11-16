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
import com.community.site.dto.response.SiteLogoResponse;
import com.community.site.dto.response.SiteLogoSetResponse;
import com.community.site.entity.SiteLogoVariant;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사이트 로고 지정·제거 — 저장 단추 없이 즉시 적용된다. 로고는 칸(언어 × 바탕, {@link SiteLogoVariant} — V237)마다 <b>그림 파일</b>(먼저 {@code POST /api/admin/files} 로 올린 것을 지정)
 * 또는 <b>SVG 소스</b> 중 하나다. 4칸 API({@code /api/admin/site/logos/**})는 매번 4칸 전체와 「이름 포함」을 돌려주고, 옛 단일 로고 API({@code /api/admin/site/logo})는
 * ① 한글·밝은 바탕용 칸을 바꾸고 바뀐 {@code logoUrl} 만 돌려준다.
 *
 * <ul>
 *   <li><b>파일 지정</b>({@link #setFile}): 올려 둔 파일({@code PENDING})만 지정할 수 있다 — 올리기만 하고 지정하기 전에는 로고로 쓰이지 않는다. 지정하면 파일이 {@code IN_USE} 가 되고
 *       그 칸의 이전 로고 파일은 장부에서 지워진다(저장소 파일은 커밋 뒤에 지운다). 이미 그 칸의 로고인 파일을 다시 지정하면 아무것도 바꾸지 않는다. 다른 칸이 쓰는 파일({@code IN_USE})은
 *       지정할 수 없다 — 파일 하나는 한 칸만 가리키므로 칸을 비울 때 파일을 지워도 다른 칸이 깨지지 않는다.</li>
 *   <li><b>SVG</b>({@link #setSvg}): 소스를 검사해({@link SvgInspector}, 정사각 요구 없음) <b>위험 요소가 있으면 거절</b>한다(정화하지 않는다). 통과한 소스(xmlns 를 맞춘 것)를 DB 에 두고
 *       주소는 서버가 만든 data URI 다 — 화면은 {@code <img src>} 한 줄로 그린다.</li>
 *   <li><b>제거</b>({@link #clear}): 그 칸이 이미 비어 있으면 아무것도 하지 않는다. 빈 칸을 어느 칸이 대신할지는 화면 규칙이다.</li>
 * </ul>
 * 사이트 설정 행을 쓰기 잠금으로 잡아 동시에 바꾸는 요청을 줄 세운다. 바뀐 일은 감사 기록({@code SITE_LOGO_CHANGE}·{@code SITE_LOGO_REMOVE}·{@code SITE_LOGO_NAME_CHANGE}, 칸은
 * {@code variant})에 남긴다 — 파일 이름·내용·SVG 소스는 남기지 않는다. 감사 기록이 남으면 {@code CacheAutoRefresh} 가 공개 사본({@code site.info})을 비운다.
 */
@Service
public class SiteLogoService {

    private static final Logger log = LoggerFactory.getLogger(SiteLogoService.class);

    private final SiteSettingRepository settings;
    private final FileRepository files;
    private final SiteAssets assets;
    private final SiteImageFiles imageFiles;
    private final AuditLogService audit;
    private final TransactionTemplate tx;
    private final AdminNotices notices;

    public SiteLogoService(SiteSettingRepository settings, FileRepository files, SiteAssets assets, SiteImageFiles imageFiles, AuditLogService audit,
                           PlatformTransactionManager txManager, AdminNotices notices) {
        this.notices = notices;
        this.settings = settings;
        this.files = files;
        this.assets = assets;
        this.imageFiles = imageFiles;
        this.audit = audit;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 바뀐 뒤의 4칸·이름 포함과, 커밋 뒤에 지울 이전 로고 파일의 키 */
    private record Changed(SiteLogoSetResponse set, String oldKey) {
    }

    // ── 4칸 API(V237) ──

    /** 4칸과 「이름 포함」 — 설정 행이 없으면(설치 전) {@code fields.site} */
    @Transactional(readOnly = true)
    public SiteLogoSetResponse read() {
        SiteSettingEntity row = settings.findById(SiteSettingEntity.ROW_ID).orElseThrow(() -> ValidationException.of("site", new MessageRef("siteSetting.missing")));
        return setOf(row);
    }

    /** 올려 둔 그림 파일을 한 칸의 로고로 삼는다 */
    public SiteLogoSetResponse setFile(AdminActor actor, String variantKey, String fileIdText) {
        return setFile(actor, variant(variantKey), fileIdText);
    }

    /** SVG 소스를 한 칸의 로고로 삼는다 — 위험 요소가 있으면 거절한다 */
    public SiteLogoSetResponse setSvg(AdminActor actor, String variantKey, String svgSource) {
        return setSvg(actor, variant(variantKey), svgSource);
    }

    /** 한 칸의 로고를 뗀다 — 이미 비어 있으면 그대로 */
    public SiteLogoSetResponse clear(AdminActor actor, String variantKey) {
        return clear(actor, variant(variantKey));
    }

    /** 「로고에 사이트 이름이 들어 있음」을 바꾼다 — 같은 값이면 아무것도 바꾸지 않는다(감사 기록도 없다) */
    public SiteLogoSetResponse setNameInLogo(AdminActor actor, Boolean nameInLogo) {
        if (nameInLogo == null) {
            throw ValidationException.of("nameInLogo", new MessageRef("siteSetting.logo.nameInLogoRequired"));
        }
        boolean included = nameInLogo;
        return tx.execute(status -> {
            SiteSettingEntity row = lockRow();
            boolean before = row.isLogoNameIncluded();
            if (before != included) {
                row.changeLogoNameIncluded(included, actor.operatorId(), Instant.now());
                audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_LOGO_NAME_CHANGE, AuditResult.SUCCESS).target("SITE", "1")
                        .with("from", before).with("to", included));
                notices.siteSettingsChanged(actor, "siteLogo");
                log.info("로고 이름 포함 여부를 바꿨다 operatorId={} to={}", actor.operatorId(), included);
            }
            return setOf(row);
        });
    }

    // ── 옛 단일 로고 API — ① 한글·밝은 바탕용 칸 ──

    /** 올려 둔 파일을 ① 로고로 삼는다 */
    public SiteLogoResponse setFile(AdminActor actor, String fileIdText) {
        return new SiteLogoResponse(setFile(actor, SiteLogoVariant.KO_LIGHT, fileIdText).logos().koLight());
    }

    /** SVG 소스를 ① 로고로 삼는다 — 위험 요소가 있으면 거절한다 */
    public SiteLogoResponse setSvg(AdminActor actor, String svgSource) {
        return new SiteLogoResponse(setSvg(actor, SiteLogoVariant.KO_LIGHT, svgSource).logos().koLight());
    }

    /** ① 로고를 뗀다 — 이미 없으면 그대로 {@code null} */
    public SiteLogoResponse clear(AdminActor actor) {
        clear(actor, SiteLogoVariant.KO_LIGHT);
        return new SiteLogoResponse(null);
    }

    // ── 안쪽 ──

    private SiteLogoSetResponse setFile(AdminActor actor, SiteLogoVariant variant, String fileIdText) {
        long fileId = parseFileId(fileIdText);
        Changed changed = tx.execute(status -> {
            SiteSettingEntity row = lockRow();
            Long currentId = row.logoFileId(variant);
            boolean same = currentId != null && currentId == fileId;
            FileEntity file = files.findWithLockById(fileId).filter(f -> f.getPurpose() == FilePurpose.SITE_LOGO)
                    .filter(f -> f.getStatus() == FileStatus.PENDING || same)
                    .orElseThrow(() -> ValidationException.of("fileId", new MessageRef("siteSetting.logo.fileNotFound")));
            if (same) {
                return new Changed(setOf(row), null); // 이미 이 파일이 이 칸의 로고다
            }
            Instant now = Instant.now();
            String oldKey = retireCurrentFile(row, variant);
            row.changeLogoFile(variant, fileId, actor.operatorId(), now);
            file.markUsed(now);
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_LOGO_CHANGE, AuditResult.SUCCESS).target("SITE", "1")
                    .with("variant", variant.key()).with("kind", "FILE").with("bytes", file.getSizeBytes()).with("width", file.getWidth())
                    .with("height", file.getHeight()));
            notices.siteSettingsChanged(actor, "siteLogo");
            return new Changed(setOf(row), oldKey);
        });
        log.info("사이트 로고를 바꿨다(파일) operatorId={} variant={}", actor.operatorId(), variant.key());
        imageFiles.deleteQuietly(oldKeys(changed.oldKey()));
        return changed.set();
    }

    private SiteLogoSetResponse setSvg(AdminActor actor, SiteLogoVariant variant, String svgSource) {
        String source = SvgInspector.inspect(svgSource, false).source();
        Changed changed = tx.execute(status -> {
            SiteSettingEntity row = lockRow();
            String oldKey = retireCurrentFile(row, variant);
            row.changeLogoSvg(variant, source, actor.operatorId(), Instant.now());
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_LOGO_CHANGE, AuditResult.SUCCESS).target("SITE", "1")
                    .with("variant", variant.key()).with("kind", "SVG").with("length", source.length()));
            notices.siteSettingsChanged(actor, "siteLogo");
            return new Changed(setOf(row), oldKey);
        });
        log.info("사이트 로고를 바꿨다(SVG) operatorId={} variant={}", actor.operatorId(), variant.key());
        imageFiles.deleteQuietly(oldKeys(changed.oldKey()));
        return changed.set();
    }

    private SiteLogoSetResponse clear(AdminActor actor, SiteLogoVariant variant) {
        Changed changed = tx.execute(status -> {
            SiteSettingEntity row = lockRow();
            if (row.logoFileId(variant) == null && row.logoSvg(variant) == null) {
                return new Changed(setOf(row), null);
            }
            String kind = row.logoSvg(variant) != null ? "SVG" : "FILE";
            String oldKey = retireCurrentFile(row, variant);
            row.clearLogo(variant, actor.operatorId(), Instant.now());
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_LOGO_REMOVE, AuditResult.SUCCESS).target("SITE", "1")
                    .with("variant", variant.key()).with("kind", kind));
            notices.siteSettingsChanged(actor, "siteLogo");
            log.info("사이트 로고를 뗐다 operatorId={} variant={}", actor.operatorId(), variant.key());
            return new Changed(setOf(row), oldKey);
        });
        imageFiles.deleteQuietly(oldKeys(changed.oldKey()));
        return changed.set();
    }

    /** 4칸 주소는 행이 가리키는 값으로 만든다 — 지금 트랜잭션 안에서 부르면 방금 바꾼 값이 보인다 */
    private SiteLogoSetResponse setOf(SiteSettingEntity row) {
        return new SiteLogoSetResponse(assets.logoUrls(row), row.isLogoNameIncluded());
    }

    private SiteSettingEntity lockRow() {
        return settings.findWithLockById(SiteSettingEntity.ROW_ID).orElseThrow(() -> ValidationException.of("site", new MessageRef("siteSetting.missing")));
    }

    /**
     * 그 칸의 지금 로고가 그림 파일이면 그 장부 행을 지우고 저장소 키를 돌려준다(커밋 뒤에 파일을 지운다). 행을 지우려면 설정 행이 먼저 다른 값을 가리켜야 하므로
     * 호출하는 쪽이 이 메서드 뒤에 {@code row.changeLogo…}·{@code row.clearLogo} 를 부른다 — 같은 트랜잭션이라 플러시 때 수정이 삭제보다 먼저 나간다.
     */
    private String retireCurrentFile(SiteSettingEntity row, SiteLogoVariant variant) {
        Long oldId = row.logoFileId(variant);
        if (oldId == null) {
            return null;
        }
        Optional<FileEntity> old = files.findById(oldId);
        old.ifPresent(files::delete);
        return old.map(FileEntity::getStorageKey).orElse(null);
    }

    private static SiteLogoVariant variant(String key) {
        return SiteLogoVariant.fromKey(key).orElseThrow(() -> ValidationException.of("variant", new MessageRef("siteSetting.logo.variantInvalid")));
    }

    private static List<String> oldKeys(String key) {
        return key == null ? List.of() : List.of(key);
    }

    private static long parseFileId(String text) {
        if (text == null || text.isBlank()) {
            throw ValidationException.of("fileId", new MessageRef("siteSetting.logo.fileRequired"));
        }
        try {
            return Long.parseLong(text.strip());
        } catch (NumberFormatException e) {
            throw ValidationException.of("fileId", new MessageRef("siteSetting.logo.fileNotFound"));
        }
    }
}
