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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사이트 로고 지정·제거 — 저장 단추 없이 즉시 적용된다(바뀐 {@code logoUrl} 만 돌려준다). 로고는 <b>그림 파일</b>(먼저 {@code POST /api/admin/files} 로 올린 것을 지정) 또는 <b>SVG 소스</b> 중 하나다.
 *
 * <ul>
 *   <li><b>파일 지정</b>({@link #setFile}): 올려 둔 파일({@code PENDING})만 지정할 수 있다 — 올리기만 하고 지정하기 전에는 로고로 쓰이지 않는다. 지정하면 파일이 {@code IN_USE} 가 되고
 *       이전 로고 파일은 장부에서 지워진다(저장소 파일은 커밋 뒤에 지운다). 이미 지금 로고인 파일을 다시 지정하면 아무것도 바꾸지 않는다.</li>
 *   <li><b>SVG</b>({@link #setSvg}): 소스를 검사해({@link SvgInspector}, 정사각 요구 없음) <b>위험 요소가 있으면 거절</b>한다(정화하지 않는다). 통과한 소스(xmlns 를 맞춘 것)를 DB 에 두고
 *       {@code logoUrl} 은 서버가 만든 data URI 다 — 화면은 {@code <img src>} 한 줄로 그린다.</li>
 *   <li><b>제거</b>({@link #clear}): 이미 로고가 없으면 아무것도 하지 않고 {@code null} 을 돌려준다.</li>
 * </ul>
 * 사이트 설정 행을 쓰기 잠금으로 잡아 동시에 바꾸는 요청을 줄 세운다. 바뀐 일은 감사 기록({@code SITE_LOGO_CHANGE}·{@code SITE_LOGO_REMOVE})에 남긴다 — 파일 이름·내용·SVG 소스는 남기지 않는다.
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

    /** 바뀐 로고와, 커밋 뒤에 지울 이전 로고 파일의 키 */
    private record Changed(String logoUrl, String oldKey) {
    }

    /** 올려 둔 파일을 로고로 삼는다 */
    public SiteLogoResponse setFile(AdminActor actor, String fileIdText) {
        long fileId = parseFileId(fileIdText);
        Changed changed = tx.execute(status -> {
            SiteSettingEntity row = lockRow();
            FileEntity file = files.findWithLockById(fileId).filter(f -> f.getPurpose() == FilePurpose.SITE_LOGO)
                    .filter(f -> f.getStatus() == FileStatus.PENDING || (row.getLogoFileId() != null && row.getLogoFileId() == fileId))
                    .orElseThrow(() -> ValidationException.of("fileId", new MessageRef("siteSetting.logo.fileNotFound")));
            if (row.getLogoFileId() != null && row.getLogoFileId() == fileId) {
                return new Changed(assets.logoUrl(row).orElse(null), null); // 이미 이 파일이 로고다
            }
            Instant now = Instant.now();
            String oldKey = retireCurrentFile(row);
            row.changeLogoFile(fileId, actor.operatorId(), now);
            file.markUsed(now);
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_LOGO_CHANGE, AuditResult.SUCCESS).target("SITE", "1")
                    .with("kind", "FILE").with("bytes", file.getSizeBytes()).with("width", file.getWidth()).with("height", file.getHeight()));
            notices.siteSettingsChanged(actor, "siteLogo");
            return new Changed(assets.logoUrl(row).orElse(null), oldKey);
        });
        log.info("사이트 로고를 바꿨다(파일) operatorId={}", actor.operatorId());
        imageFiles.deleteQuietly(oldKeys(changed.oldKey()));
        return new SiteLogoResponse(changed.logoUrl());
    }

    /** SVG 소스를 로고로 삼는다 — 위험 요소가 있으면 거절한다 */
    public SiteLogoResponse setSvg(AdminActor actor, String svgSource) {
        String source = SvgInspector.inspect(svgSource, false).source();
        Changed changed = tx.execute(status -> {
            SiteSettingEntity row = lockRow();
            String oldKey = retireCurrentFile(row);
            row.changeLogoSvg(source, actor.operatorId(), Instant.now());
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_LOGO_CHANGE, AuditResult.SUCCESS).target("SITE", "1")
                    .with("kind", "SVG").with("length", source.length()));
            notices.siteSettingsChanged(actor, "siteLogo");
            return new Changed(SiteAssets.svgDataUri(source), oldKey);
        });
        log.info("사이트 로고를 바꿨다(SVG) operatorId={}", actor.operatorId());
        imageFiles.deleteQuietly(oldKeys(changed.oldKey()));
        return new SiteLogoResponse(changed.logoUrl());
    }

    /** 로고를 뗀다 — 이미 없으면 그대로 {@code null} */
    public SiteLogoResponse clear(AdminActor actor) {
        Changed changed = tx.execute(status -> {
            SiteSettingEntity row = lockRow();
            if (row.getLogoFileId() == null && row.getLogoSvg() == null) {
                return new Changed(null, null);
            }
            String kind = row.getLogoSvg() != null ? "SVG" : "FILE";
            String oldKey = retireCurrentFile(row);
            row.clearLogo(actor.operatorId(), Instant.now());
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_LOGO_REMOVE, AuditResult.SUCCESS).target("SITE", "1").with("kind", kind));
            notices.siteSettingsChanged(actor, "siteLogo");
            return new Changed(null, oldKey);
        });
        if (changed.oldKey() != null) {
            log.info("사이트 로고를 뗐다 operatorId={}", actor.operatorId());
        }
        imageFiles.deleteQuietly(oldKeys(changed.oldKey()));
        return new SiteLogoResponse(null);
    }

    // ── 안쪽 ──

    private SiteSettingEntity lockRow() {
        return settings.findWithLockById(SiteSettingEntity.ROW_ID).orElseThrow(() -> ValidationException.of("site", new MessageRef("siteSetting.missing")));
    }

    /**
     * 지금 로고가 그림 파일이면 그 장부 행을 지우고 저장소 키를 돌려준다(커밋 뒤에 파일을 지운다). 행을 지우려면 설정 행이 먼저 다른 값을 가리켜야 하므로
     * 호출하는 쪽이 이 메서드 뒤에 {@code row.change…} 를 부른다 — 같은 트랜잭션이라 플러시 때 수정이 삭제보다 먼저 나간다.
     */
    private String retireCurrentFile(SiteSettingEntity row) {
        Long oldId = row.getLogoFileId();
        if (oldId == null) {
            return null;
        }
        Optional<FileEntity> old = files.findById(oldId);
        old.ifPresent(files::delete);
        return old.map(FileEntity::getStorageKey).orElse(null);
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
