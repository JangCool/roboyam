package com.community.ad.service;

import com.community.ad.dto.AdAdminView;
import com.community.media.entity.FileEntity;
import com.community.media.entity.FilePurpose;
import com.community.media.repository.master.FileRepository;
import com.community.media.service.ImageStorage;
import com.community.site.service.LogoImageProcessor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 운영자 광고 이미지 올리기({@code POST /api/admin/ads/images}, multipart {@code file}) — 광고 일정표 수동 등록 서랍의 배너 소재. 관리자 그림과 같은 검사·다시 만들기
 * ({@link LogoImageProcessor} — PNG·JPG·GIF·WEBP, 2MB, GIF 는 PNG)를 거쳐 공개 저장소(/media)에 둔다(광고는 공개 노출). 파일 장부는 용도 {@code BIZ_AD_IMAGE}·올린 운영자로
 * PENDING, 등록에 쓰이면 IN_USE.
 */
@Component
public class AdImageUploader {

    private static final Logger log = LoggerFactory.getLogger(AdImageUploader.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneOffset.UTC);

    private final LogoImageProcessor processor;
    private final ImageStorage storage;
    private final FileRepository files;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;

    public AdImageUploader(LogoImageProcessor processor, ImageStorage storage, FileRepository files, SnowflakeIdGenerator ids, PlatformTransactionManager transactions) {
        this.processor = processor;
        this.storage = storage;
        this.files = files;
        this.ids = ids;
        this.tx = new TransactionTemplate(transactions);
    }

    public AdAdminView.Image upload(long operatorId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ValidationException.of("file", new MessageRef("bizapp.file.required"));
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ValidationException.of("file", new MessageRef("site.image.corrupt"));
        }
        LogoImageProcessor.Processed processed = processor.process(data, "bizapp.image.type", "bizapp.image.size");
        Instant now = Instant.now();
        String key = "biz-ad/op/" + MONTH.format(now) + "/" + token() + "." + processed.format().extension();
        storage.put(key, processed.data());
        long fileId = ids.nextId();
        try {
            tx.executeWithoutResult(status -> files.persist(FileEntity.pending(fileId, FilePurpose.BIZ_AD_IMAGE, key, processed.format().contentType(),
                    processed.data().length, processed.width(), processed.height(), operatorId, Instant.now())));
        } catch (RuntimeException e) {
            try {
                storage.delete(key);
            } catch (RuntimeException ignored) {
                log.warn("장부에 넣지 못한 광고 이미지 파일을 지우지 못했다 key={}", key);
            }
            throw e;
        }
        String original = file.getOriginalFilename() == null ? key.substring(key.lastIndexOf('/') + 1) : file.getOriginalFilename().replace('\\', '/');
        return new AdAdminView.Image(Long.toString(fileId), original.substring(original.lastIndexOf('/') + 1), processed.data().length,
                processed.format().contentType(), storage.publicUrl(key));
    }

    private static String token() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
