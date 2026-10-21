package com.community.site.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.permission.dto.AdminActor;
import com.community.site.dto.response.SiteOgImageResponse;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 링크 미리보기 이미지(og:image) 조회·올리기·기본으로 되돌리기 — 저장 단추 없이 즉시 적용된다. 사이트 아이콘({@link SiteIconService})과 같은 틀이다:
 * 설정 행에 버전 토큰({@code og_token})만 두고 파일(1200×630 불투명 PNG 한 장)은 저장소 {@code site-og/{버전}/og.png} 에 있다.
 * 저장 순서는 검사·처리 → 저장소 쓰기 → DB 교체 트랜잭션 → 커밋 뒤 예전 파일 삭제(실패하면 새 파일 삭제).
 */
@Service
public class SiteOgImageService {

    private static final Logger log = LoggerFactory.getLogger(SiteOgImageService.class);

    private final SiteSettingRepository settings;
    private final SiteOgImageProcessor processor;
    private final SiteImageFiles imageFiles;
    private final SiteAssets assets;
    private final AuditLogService audit;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public SiteOgImageService(SiteSettingRepository settings, SiteOgImageProcessor processor, SiteImageFiles imageFiles, SiteAssets assets, AuditLogService audit,
                              PlatformTransactionManager txManager) {
        this.settings = settings;
        this.processor = processor;
        this.imageFiles = imageFiles;
        this.assets = assets;
        this.audit = audit;
        this.tx = new TransactionTemplate(txManager);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    public SiteOgImageResponse get() {
        String version = readTx.execute(status -> settings.findById(SiteSettingEntity.ROW_ID).orElseThrow(() -> new CommonException(ErrorCode.NOT_FOUND)).getOgToken());
        return view(version);
    }

    /** PNG 로 올린다 — 검사에 걸리면 아무것도 저장하지 않는다 */
    public SiteOgImageResponse upload(AdminActor actor, MultipartFile file) {
        if (file == null) {
            throw ImageProblems.file("file.required"); // multipart 에 file 파트가 없다
        }
        if (file.isEmpty()) {
            throw ImageProblems.empty();
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ImageProblems.corrupt();
        }
        SiteOgImageProcessor.Result result = processor.process(data);

        String version = imageFiles.newVersion();
        String key = SiteMediaKeys.og(version);
        imageFiles.writeAll(Map.of(key, result.data()));
        String oldVersion;
        try {
            oldVersion = tx.execute(status -> {
                SiteSettingEntity row = lockRow();
                String previous = row.getOgToken();
                row.changeOgToken(version, actor.operatorId(), Instant.now());
                audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_OG_CHANGE, AuditResult.SUCCESS).target("SITE", "1")
                        .with("sourceWidth", result.sourceWidth()).with("sourceHeight", result.sourceHeight()));
                return previous;
            });
        } catch (RuntimeException e) {
            imageFiles.deleteQuietly(List.of(key)); // 주인 없는 파일이 남지 않게
            throw e;
        }
        log.info("링크 미리보기 이미지를 올렸다 source={}x{} operatorId={}", result.sourceWidth(), result.sourceHeight(), actor.operatorId());
        if (oldVersion != null) {
            imageFiles.deleteQuietly(List.of(SiteMediaKeys.og(oldVersion)));
        }
        return view(version);
    }

    /** 올려 둔 이미지를 지우고 기본 이미지로 되돌린다 — 이미 기본이면 그대로 */
    public SiteOgImageResponse reset(AdminActor actor) {
        String oldVersion = tx.execute(status -> {
            SiteSettingEntity row = lockRow();
            String previous = row.getOgToken();
            if (previous != null) {
                row.changeOgToken(null, actor.operatorId(), Instant.now());
                audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_OG_RESET, AuditResult.SUCCESS).target("SITE", "1"));
            }
            return previous;
        });
        if (oldVersion != null) {
            log.info("링크 미리보기 이미지를 기본으로 되돌렸다 operatorId={}", actor.operatorId());
            imageFiles.deleteQuietly(List.of(SiteMediaKeys.og(oldVersion)));
        }
        return view(null);
    }

    private SiteSettingEntity lockRow() {
        return settings.findWithLockById(SiteSettingEntity.ROW_ID).orElseThrow(() -> ValidationException.of("site", new MessageRef("siteSetting.missing")));
    }

    private SiteOgImageResponse view(String version) {
        if (version == null) {
            return new SiteOgImageResponse(false, null, null, null, SiteMediaKeys.DEFAULT_OG_URL);
        }
        return new SiteOgImageResponse(true, assets.ogUrl(version), SiteMediaKeys.OG_WIDTH, SiteMediaKeys.OG_HEIGHT, SiteMediaKeys.DEFAULT_OG_URL);
    }
}
