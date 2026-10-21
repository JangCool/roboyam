package com.community.site.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.notification.service.AdminNotices;
import com.community.permission.dto.AdminActor;
import com.community.site.dto.SiteIconUrls;
import com.community.site.dto.response.SiteIconResponse;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사이트 아이콘 조회·올리기·기본으로 되돌리기 — 저장 단추 없이 즉시 적용된다. 사이트 이름({@code site_settings} 행)에 버전 토큰({@code icon_token})만 두고 파일은 저장소에 있다
 * ({@code site-icon/{버전}/…} 다섯 장 — 32·48·180(흰 배경)·192·512 PNG).
 *
 * <p><b>올리기</b>(PNG 파일 {@link #upload} · SVG 소스 {@link #uploadSvg}): 검사·처리({@link SiteIconProcessor}, 규칙 위반은 {@code fields.file} · {@code fields.svgSource}) →
 * 저장소에 새 버전 파일 다섯 장 → 설정 행을 쓰기 잠금으로 잡아 버전 교체(행이 없으면 {@code fields.site}) → 커밋 뒤 예전 버전 파일 삭제. DB 에 반영하지 못하면 방금 쓴 새 파일을 지운다.
 * 이미지 처리·저장소 입출력 동안 DB 연결을 붙잡지 않으려고 클래스 트랜잭션을 걸지 않고 DB 쓰기만 {@link TransactionTemplate} 으로 감싼다.
 *
 * <p><b>되돌리기</b>({@link #reset}): 이미 기본이면 아무것도 바꾸지 않고 같은 응답(200)을 준다.
 */
@Service
public class SiteIconService {

    private static final Logger log = LoggerFactory.getLogger(SiteIconService.class);

    private final SiteSettingRepository settings;
    private final SiteIconProcessor processor;
    private final SiteImageFiles imageFiles;
    private final SiteAssets assets;
    private final AuditLogService audit;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;
    private final AdminNotices notices;

    public SiteIconService(SiteSettingRepository settings, SiteIconProcessor processor, SiteImageFiles imageFiles, SiteAssets assets, AuditLogService audit,
                           PlatformTransactionManager txManager, AdminNotices notices) {
        this.notices = notices;
        this.settings = settings;
        this.processor = processor;
        this.imageFiles = imageFiles;
        this.assets = assets;
        this.audit = audit;
        this.tx = new TransactionTemplate(txManager);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    /** 지금 아이콘 상태. DB 를 바로 읽으므로 다른 서버에서 방금 바꾼 것도 보인다 */
    public SiteIconResponse get() {
        String version = readTx.execute(status -> settings.findById(SiteSettingEntity.ROW_ID).orElseThrow(() -> new CommonException(ErrorCode.NOT_FOUND)).getIconToken());
        return view(version);
    }

    /** PNG 파일로 아이콘을 올린다 — 검사에 걸리면 아무것도 저장하지 않는다 */
    public SiteIconResponse upload(AdminActor actor, MultipartFile file) {
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
        return store(actor, processor.process(data), "PNG");
    }

    /** SVG 소스로 아이콘을 올린다(서버가 PNG 로 바꿔 저장한다) */
    public SiteIconResponse uploadSvg(AdminActor actor, String svgSource) {
        return store(actor, processor.processSvg(svgSource), "SVG");
    }

    /** 올려 둔 아이콘을 지우고 기본 아이콘으로 되돌린다 — 이미 기본이면 그대로 */
    public SiteIconResponse reset(AdminActor actor) {
        String oldVersion = tx.execute(status -> {
            SiteSettingEntity row = lockRow();
            String previous = row.getIconToken();
            if (previous != null) {
                row.changeIconToken(null, actor.operatorId(), Instant.now());
                audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_ICON_RESET, AuditResult.SUCCESS).target("SITE", "1"));
                notices.siteSettingsChanged(actor, "siteIcon");
            }
            return previous;
        });
        if (oldVersion != null) {
            log.info("사이트 아이콘을 기본으로 되돌렸다 operatorId={}", actor.operatorId());
            imageFiles.deleteQuietly(SiteMediaKeys.iconKeys(oldVersion));
        }
        return view(null);
    }

    // ── 안쪽 ──

    /** 만든 다섯 장을 새 버전으로 저장하고 설정 행의 버전을 바꾼다 — PNG·SVG 입구가 같은 길이다 */
    private SiteIconResponse store(AdminActor actor, SiteIconProcessor.Result result, String kind) {
        String version = imageFiles.newVersion();
        Map<String, byte[]> files = new LinkedHashMap<>();
        result.files().forEach((icon, bytes) -> files.put(SiteMediaKeys.icon(version, icon), bytes));
        imageFiles.writeAll(files);
        String oldVersion;
        try {
            oldVersion = tx.execute(status -> {
                SiteSettingEntity row = lockRow();
                String previous = row.getIconToken();
                row.changeIconToken(version, actor.operatorId(), Instant.now());
                audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_ICON_CHANGE, AuditResult.SUCCESS).target("SITE", "1")
                        .with("kind", kind).with("sourceSide", result.sourceSide()));
                notices.siteSettingsChanged(actor, "siteIcon");
                return previous;
            });
        } catch (RuntimeException e) {
            imageFiles.deleteQuietly(List.copyOf(files.keySet())); // 주인 없는 파일이 남지 않게
            throw e;
        }
        log.info("사이트 아이콘을 올렸다 kind={} source={}px operatorId={}", kind, result.sourceSide(), actor.operatorId());
        if (oldVersion != null) {
            imageFiles.deleteQuietly(SiteMediaKeys.iconKeys(oldVersion));
        }
        return view(version);
    }

    private SiteSettingEntity lockRow() {
        return settings.findWithLockById(SiteSettingEntity.ROW_ID).orElseThrow(() -> ValidationException.of("site", new MessageRef("siteSetting.missing")));
    }

    private SiteIconResponse view(String version) {
        if (version == null) {
            return new SiteIconResponse(false, null, SiteMediaKeys.DEFAULT_ICON_URL);
        }
        SiteIconUrls urls = assets.iconUrls(version);
        return new SiteIconResponse(true, urls.icon512(), SiteMediaKeys.DEFAULT_ICON_URL);
    }

}
