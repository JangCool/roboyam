package com.community.bizapplication.service;

import com.community.bizapplication.dto.BizMemberView;
import com.community.business.entity.BusinessServiceKind;
import com.community.business.service.BusinessEligibility;
import com.community.media.entity.FileEntity;
import com.community.media.repository.master.FileRepository;
import com.community.media.service.ImageStorage;
import com.community.media.service.PostImageProcessor;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사업자 광고 이미지 올리기({@code POST /api/business/ad-images}, multipart {@code file}) — 광고 이용 자격이 있는 사업자만(409 {@code BUSINESS_NOT_APPROVED}·{@code BUSINESS_RE_REVIEW}).
 * 글 이미지와 같은 검사·다시 만들기({@link PostImageProcessor} — JPEG·PNG·WebP·GIF, 10MB, 디코딩 실패 400 {@code fields.file})를 거쳐 공개 저장소(/media)에 둔다(광고는 공개 노출이라).
 * 파일 장부는 용도 {@code BIZ_AD_IMAGE} PENDING 으로 만들고, 신청·소재 바꾸기에 쓰이면 IN_USE — 쓰이지 않은 채 오래되면 「올리다 만 파일 정리」 배치가 지운다.
 */
@Service
public class BizAdImageService {

    private static final Logger log = LoggerFactory.getLogger(BizAdImageService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneOffset.UTC);

    private final BusinessEligibility eligibility;
    private final PostImageProcessor processor;
    private final ImageStorage storage;
    private final FileRepository files;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;

    public BizAdImageService(BusinessEligibility eligibility, PostImageProcessor processor, ImageStorage storage, FileRepository files, SnowflakeIdGenerator ids,
                             PlatformTransactionManager transactions) {
        this.eligibility = eligibility;
        this.processor = processor;
        this.storage = storage;
        this.files = files;
        this.ids = ids;
        this.tx = new TransactionTemplate(transactions);
    }

    public BizMemberView.File upload(long userId, MultipartFile file) {
        eligibility.requireApproved(userId, BusinessServiceKind.AD);
        if (file == null || file.isEmpty()) {
            throw ValidationException.of("file", new MessageRef("bizapp.file.required"));
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ValidationException.of("file", new MessageRef("site.image.corrupt"));
        }
        PostImageProcessor.Processed processed = processor.process(data);
        Instant now = Instant.now();
        String key = "biz-ad/" + MONTH.format(now) + "/" + token() + "." + processed.format().extension();
        storage.put(key, processed.data());
        long fileId = ids.nextId();
        try {
            tx.executeWithoutResult(status -> files.persist(FileEntity.pendingBizAdImage(fileId, key, processed.format().contentType(), processed.data().length,
                    processed.width(), processed.height(), userId, Instant.now())));
        } catch (RuntimeException e) {
            try {
                storage.delete(key);
            } catch (RuntimeException ignored) {
                log.warn("장부에 넣지 못한 광고 이미지 파일을 지우지 못했다 key={}", key);
            }
            throw e;
        }
        String name = file.getOriginalFilename() == null ? key.substring(key.lastIndexOf('/') + 1)
                : file.getOriginalFilename().replace('\\', '/').substring(file.getOriginalFilename().replace('\\', '/').lastIndexOf('/') + 1);
        return new BizMemberView.File(Long.toString(fileId), name, processed.data().length, processed.format().contentType(), storage.publicUrl(key));
    }

    private static String token() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
