package com.community.media.service;

import com.community.media.dto.response.FileUploadResponse;
import com.community.media.entity.FileEntity;
import com.community.media.entity.FilePurpose;
import com.community.media.repository.master.FileRepository;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.permission.exception.PermissionDeniedException;
import com.community.site.service.LogoImageProcessor;
import com.community.site.service.SiteImageFiles;
import com.community.site.service.SiteMediaKeys;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
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
 * 파일 올리기({@code POST /api/admin/files}, multipart {@code file} + {@code purpose}) — 사이트 로고 그림({@code SITE_LOGO}, 권한 site.manage)과
 * 하이라이트 대표 이미지({@code HIGHLIGHT_IMAGE}, 권한 highlight.manage — 하이라이트 만들기·고치기의 {@code imageFileId} 로 지정한다. 같은 검사·다시 만들기, 키 {@code highlight/{버전}/image.{확장자}}).
 * 올린 파일은 {@code PENDING} 장부 행과 저장소 파일이 되고, 로고로 지정하기({@code PUT /api/admin/site/logo})까지는 아무 데도 쓰이지 않는다(공개 주소도 알려 주지 않는다).
 * 지정하지 않고 둔 파일은 「올리다 만 파일 정리」 배치({@code file-orphan-cleanup})가 지운다.
 *
 * <p>순서: 입력 확인 → 파일 검사·다시 만들기(트랜잭션 밖 — 이미지 처리 동안 DB 연결을 붙잡지 않는다) → 저장소 쓰기 → 장부 행 저장(트랜잭션) → 실패하면 쓴 파일을 지운다.
 */
@Service
public class FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);
    /** 하이라이트 대표 이미지의 저장소 키 머리 — {@code highlight/{버전}/image.{png|jpg|webp}} */
    static final String HIGHLIGHT_KEY_PREFIX = "highlight/";

    private final LogoImageProcessor logoProcessor;
    private final SiteImageFiles imageFiles;
    private final FileRepository files;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;

    public FileUploadService(LogoImageProcessor logoProcessor, SiteImageFiles imageFiles, FileRepository files, SnowflakeIdGenerator ids,
                             PlatformTransactionManager txManager) {
        this.logoProcessor = logoProcessor;
        this.imageFiles = imageFiles;
        this.files = files;
        this.ids = ids;
        this.tx = new TransactionTemplate(txManager);
    }

    public FileUploadResponse upload(AdminActor actor, String purposeText, MultipartFile file) {
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        FilePurpose purpose = purposeOf(purposeText);
        if (purpose == null) {
            errors.put("purpose", new MessageRef("file.purpose.unknown"));
        }
        if (file == null || file.isEmpty()) {
            // 빈 파일도 "파일을 고르세요" 가 아니라 이미지 검사의 "비어 있는 파일" 이 맞지만, 칸이 아예 없는 요청과 한 문구로 알린다
            errors.put("file", new MessageRef(file == null ? "file.required" : "site.image.empty"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        if (!actor.can(requiredPermission(purpose))) {
            throw new PermissionDeniedException();
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException(Map.of("file", new MessageRef("site.image.corrupt")));
        }
        // 로고와 하이라이트 대표 이미지는 같은 검사·다시 만들기(PNG·JPG·GIF·WEBP, 2MB, 한 변 4000px)를 쓰고 거절 문구만 다르다
        LogoImageProcessor.Processed processed = purpose == FilePurpose.HIGHLIGHT_IMAGE
                ? logoProcessor.process(data, "highlight.image.type", "highlight.image.size")
                : logoProcessor.process(data);

        String version = imageFiles.newVersion();
        String key = purpose == FilePurpose.HIGHLIGHT_IMAGE ? HIGHLIGHT_KEY_PREFIX + version + "/image." + processed.format().extension()
                : SiteMediaKeys.logo(version, processed.format().extension());
        imageFiles.writeAll(Map.of(key, processed.data()));
        long fileId = ids.nextId();
        try {
            tx.executeWithoutResult(status -> files.persist(FileEntity.pending(fileId, purpose, key, processed.format().contentType(), processed.data().length,
                    processed.width(), processed.height(), actor.operatorId(), Instant.now())));
        } catch (RuntimeException e) {
            imageFiles.deleteQuietly(List.of(key)); // 장부에 없는 파일이 남지 않게
            throw e;
        }
        log.info("파일을 올렸다 fileId={} purpose={} bytes={} operatorId={}", fileId, purpose, processed.data().length, actor.operatorId());
        return new FileUploadResponse(Long.toString(fileId));
    }

    private static FilePurpose purposeOf(String text) {
        if (text == null) {
            return null;
        }
        try {
            FilePurpose purpose = FilePurpose.valueOf(text.strip());
            return purpose == FilePurpose.POST_IMAGE || purpose == FilePurpose.BIZ_AD_IMAGE ? null : purpose; // 글 이미지·사업자 광고 이미지는 공개 사이트에서만 올린다
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 용도마다 올리는 데 필요한 권한 */
    private static Permission requiredPermission(FilePurpose purpose) {
        return switch (purpose) {
            case SITE_LOGO -> Permission.SITE_MANAGE;
            case HIGHLIGHT_IMAGE -> Permission.HIGHLIGHT_MANAGE;
            case POST_IMAGE, BIZ_AD_IMAGE -> throw new IllegalStateException("글 이미지·사업자 광고 이미지는 관리자 앱에서 올리지 않는다");
        };
    }
}
