package com.community.media.service;

import com.community.media.entity.FileEntity;
import com.community.media.repository.master.FileRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
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

/**
 * 글 본문 이미지 한 장을 검사·다시 만들고({@link PostImageProcessor}) 저장소에 쓴 뒤 장부 행({@code files}, PENDING 글 이미지 — 올린 회원 = {@code userId})을 남긴다.
 * 공개 사이트 글 이미지({@code POST /api/images})와 관리자 공지 본문 이미지({@code POST /api/admin/notices/images})가 같은 규칙을 쓴다 — 누가 올릴 수 있는지·속도 한도는 부르는 쪽이 본다.
 *
 * <p>순서: 검사·다시 만들기(트랜잭션 밖 — 이미지 처리 동안 DB 연결을 붙잡지 않는다) → 저장소 쓰기 → 장부 행 저장(짧은 트랜잭션) → 실패하면 쓴 파일을 지운다.
 * 글에 쓰이지 않은 채 {@code roboyam.media.orphan-retention-hours} 가 지나면 「올리다 만 파일 정리」 배치가 지운다. 글에 붙이는 일은 {@code PostImages#bind}.
 *
 * <p>저장소 키는 {@code post-image/{UTC 년월}/{무작위 32자}.{확장자}} — 주소를 짐작할 수 없고, 한 번 쓴 파일은 고치지 않는다(1년 immutable 캐시).
 */
@Component
public class PostImageUploads {

    private static final Logger log = LoggerFactory.getLogger(PostImageUploads.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneOffset.UTC);

    /** 올린 이미지 — 에디터가 본문에 {@code <img data-image-id="{id}">} 표식을 넣고, 고르는 동안 {@code url}·크기로 미리 보인다 */
    public record Stored(long id, String url, int width, int height) {
    }

    private final PostImageProcessor processor;
    private final ImageStorage storage;
    private final FileRepository files;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;

    public PostImageUploads(PostImageProcessor processor, ImageStorage storage, FileRepository files, SnowflakeIdGenerator ids, PlatformTransactionManager txManager) {
        this.processor = processor;
        this.storage = storage;
        this.files = files;
        this.ids = ids;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * 검사·저장 — 틀린 파일은 {@code 400 VALIDATION fields.file}({@link PostImageProcessor} 의 규칙).
     *
     * @param userId 올린 회원(글을 저장할 때 「내가 올린 이미지」인지 이 값으로 본다)
     */
    public Stored store(long userId, byte[] data) {
        PostImageProcessor.Processed processed = processor.process(data);
        String key = "post-image/" + MONTH.format(Instant.now()) + "/" + token() + "." + processed.format().extension();
        storage.put(key, processed.data());
        long fileId = ids.nextId();
        try {
            tx.executeWithoutResult(status -> files.persist(FileEntity.pendingPostImage(fileId, key, processed.format().contentType(), processed.data().length,
                    processed.width(), processed.height(), userId, Instant.now())));
        } catch (RuntimeException e) {
            deleteQuietly(key); // 장부에 없는 파일이 남지 않게
            throw e;
        }
        log.info("글 이미지를 올렸다 fileId={} bytes={} userId={}", fileId, processed.data().length, userId);
        return new Stored(fileId, storage.publicUrl(key), processed.width(), processed.height());
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("장부에 넣지 못한 글 이미지 파일을 지우지 못했다 key={}", key, e);
        }
    }

    private static String token() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
