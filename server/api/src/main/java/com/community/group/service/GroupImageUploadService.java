package com.community.group.service;

import com.community.group.dto.response.GroupImageUploadView;
import com.community.group.exception.GroupWriteBlockedException;
import com.community.media.entity.FileEntity;
import com.community.media.repository.master.FileRepository;
import com.community.media.service.ImageStorage;
import com.community.media.service.PostImageProcessor;
import com.community.media.service.PostImageRateLimiter;
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
 * 소모임 대표 이미지 올리기({@code POST /api/groups/images}, multipart {@code file}) — 로그인한 회원만(비회원 401 은 {@code SecurityConfig}).
 *
 * <p>순서: 401 → 쓰기 자격(403 {@code EMAIL_NOT_VERIFIED} → {@code ACCOUNT_RESTRICTED} → {@code BUSINESS_ACCOUNT_RESTRICTED} — 개설 신청·정보 수정과 같은 {@link GroupAccess#requireWriter})
 * → 파일 칸({@code 400 fields.file}) → 1분 속도(글 이미지와 같은 회원 단위 한도 {@code 429 IMAGE_RATE_LIMITED}) → 검사·다시 만들기({@link PostImageProcessor} — JPEG·PNG·WebP·GIF 10MB,
 * 움직이는 GIF·WebP 는 원본 유지, 디코딩 실패 {@code 400 fields.file}, 트랜잭션 밖) → 공개 저장소 쓰기 → 장부 행({@code files}, 용도 GROUP_IMAGE, PENDING) → 실패하면 쓴 파일을 지운다.
 * 어느 소모임에도 붙지 않은 채 {@code roboyam.media.orphan-retention-hours}(24시간)가 지나면 「올리다 만 파일 정리」 배치가 지운다. 하루 한도는 세지 않는다(글 이미지 한도는 글 이미지만 센다).
 *
 * <p>저장소 키는 {@code group/{UTC 년월}/{무작위 32자}.{확장자}} — 주소를 짐작할 수 없고, 한 번 쓴 파일은 고치지 않는다.
 */
@Service
public class GroupImageUploadService {

    private static final Logger log = LoggerFactory.getLogger(GroupImageUploadService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneOffset.UTC);

    private final GroupAccess access;
    private final PostImageRateLimiter limiter;
    private final PostImageProcessor processor;
    private final ImageStorage storage;
    private final FileRepository files;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;

    public GroupImageUploadService(GroupAccess access, PostImageRateLimiter limiter, PostImageProcessor processor, ImageStorage storage, FileRepository files,
                                   SnowflakeIdGenerator ids, PlatformTransactionManager transactions) {
        this.access = access;
        this.limiter = limiter;
        this.processor = processor;
        this.storage = storage;
        this.files = files;
        this.ids = ids;
        this.tx = new TransactionTemplate(transactions);
    }

    public GroupImageUploadView upload(Long userId, MultipartFile file) {
        long me = GroupAccess.requireUser(userId);
        String blocked = GroupAccess.writeBlocked(access.actor(me));
        if (blocked != null) {
            throw new GroupWriteBlockedException(blocked);
        }
        if (file == null || file.isEmpty()) {
            throw ValidationException.of("file", new MessageRef(file == null ? "file.required" : "site.image.empty"));
        }
        limiter.acquire(me);
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ValidationException.of("file", new MessageRef("site.image.corrupt"));
        }
        PostImageProcessor.Processed processed = processor.process(data);

        String key = "group/" + MONTH.format(Instant.now()) + "/" + token() + "." + processed.format().extension();
        storage.put(key, processed.data());
        long fileId = ids.nextId();
        try {
            tx.executeWithoutResult(status -> files.persist(FileEntity.pendingGroupImage(fileId, key, processed.format().contentType(), processed.data().length,
                    processed.width(), processed.height(), me, Instant.now())));
        } catch (RuntimeException e) {
            try {
                storage.delete(key); // 장부에 없는 파일이 남지 않게
            } catch (RuntimeException ignored) {
                log.warn("장부에 넣지 못한 소모임 이미지 파일을 지우지 못했다 key={}", key);
            }
            throw e;
        }
        log.info("소모임 대표 이미지를 올렸다 fileId={} bytes={} userId={}", fileId, processed.data().length, me);
        return new GroupImageUploadView(Long.toString(fileId), storage.publicUrl(key), processed.width(), processed.height());
    }

    private static String token() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
