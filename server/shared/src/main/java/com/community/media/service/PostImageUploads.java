package com.community.media.service;

import com.community.media.entity.FileEntity;
import com.community.media.repository.master.FileRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 글 본문 이미지 한 장을 검사·다시 만들고({@link PostImageProcessor}) 목록 썸네일을 만든 뒤({@link PostThumbnailMaker}) 저장소에 쓰고 장부 행({@code files}, PENDING —
 * 올린 회원 = {@code userId})을 남긴다. 공개 사이트 글 이미지({@code POST /api/images})와 관리자 공지 본문 이미지({@code POST /api/admin/notices/images})가 같은 규칙을 쓴다
 * — 누가 올릴 수 있는지·속도 한도는 부르는 쪽이 본다. 대표 썸네일로 따로 올리는 그림(용도 {@code POST_THUMBNAIL} — 글쓴이 {@link #storeThumbnailByUser},
 * 운영자 {@link #storeThumbnailByOperator})도 같은 검사·같은 썸네일 규칙이고 키 머리만 {@code post-thumb/} 이다.
 *
 * <p>순서(docs/features/list-thumbnail-contract.md 「저장·데이터」): 검사·다시 만들기·썸네일 만들기(트랜잭션 밖 — 이미지 처리 동안 DB 연결을 붙잡지 않는다) → 원본 쓰기
 * → 썸네일 쓰기(실패하면 원본도 지우고 503 {@code STORAGE_UNAVAILABLE}) → 장부 행 저장(짧은 트랜잭션 — 실패하면 둘 다 지운다). 썸네일 <b>만들기</b>가 실패하면 올리기는 성공하고
 * {@code thumbnail_key} 만 비운다(경고 로그 — 목록은 원본 주소를 쓴다).
 * 글에 쓰이지 않은 채 {@code irobit.media.orphan-retention-hours} 가 지나면 「올리다 만 파일 정리」 배치가 둘 다 지운다. 글에 붙이는 일은 {@code PostImages#bind}.
 *
 * <p>저장소 키는 {@code post-image/{UTC 년월}/{무작위 32자}.{확장자}}, 썸네일은 같은 자리의 {@code {같은 32자}-t.{jpg|png}} — 주소를 짐작할 수 없고, 한 번 쓴 파일은
 * 고치지 않는다(1년 immutable 캐시).
 */
@Component
public class PostImageUploads {

    private static final Logger log = LoggerFactory.getLogger(PostImageUploads.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneOffset.UTC);
    /** 글 본문 이미지의 저장소 키 머리 */
    public static final String POST_IMAGE_PREFIX = "post-image/";
    /** 대표 썸네일 그림의 저장소 키 머리(V206) */
    public static final String POST_THUMB_PREFIX = "post-thumb/";
    /** 목록 썸네일 키의 꼬리 — 원본 {@code …/{32자}.{확장자}} 의 짝은 {@code …/{32자}-t.{jpg|png}} */
    private static final String THUMB_SUFFIX = "-t.";

    /**
     * 올린 이미지 — 에디터가 본문에 {@code <img data-image-id="{id}">} 표식을 넣고, 고르는 동안 {@code url}·크기로 미리 보인다.
     *
     * @param thumbnailUrl 목록 썸네일 주소 — 만들지 못했으면 원본 주소({@code url})와 같다
     */
    public record Stored(long id, String url, int width, int height, String thumbnailUrl) {
    }

    private final PostImageProcessor processor;
    private final PostThumbnailMaker thumbnails;
    private final ImageStorage storage;
    private final FileRepository files;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;

    public PostImageUploads(PostImageProcessor processor, PostThumbnailMaker thumbnails, ImageStorage storage, FileRepository files, SnowflakeIdGenerator ids,
                            PlatformTransactionManager txManager) {
        this.processor = processor;
        this.thumbnails = thumbnails;
        this.storage = storage;
        this.files = files;
        this.ids = ids;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 장부 행을 만드는 쪽 — 용도·올린 사람이 다르다 */
    @FunctionalInterface
    private interface Ledger {
        FileEntity create(long id, String key, PostImageProcessor.Processed processed, Instant now);
    }

    /**
     * 글 본문 이미지 검사·저장 — 틀린 파일은 {@code 400 VALIDATION fields.file}({@link PostImageProcessor} 의 규칙).
     *
     * @param userId 올린 회원(글을 저장할 때 「내가 올린 이미지」인지 이 값으로 본다)
     */
    public Stored store(long userId, byte[] data) {
        return store(POST_IMAGE_PREFIX, data, "userId=" + userId,
                (id, key, p, now) -> FileEntity.pendingPostImage(id, key, p.format().contentType(), p.data().length, p.width(), p.height(), userId, now));
    }

    /** 글쓴이가 대표 썸네일로 따로 올리는 그림(용도 {@code POST_THUMBNAIL}) — 글 이미지와 같은 검사 */
    public Stored storeThumbnailByUser(long userId, byte[] data) {
        return store(POST_THUMB_PREFIX, data, "userId=" + userId,
                (id, key, p, now) -> FileEntity.pendingPostThumbnailByUser(id, key, p.format().contentType(), p.data().length, p.width(), p.height(), userId, now));
    }

    /** 운영자가 대표 썸네일로 올리는 그림(용도 {@code POST_THUMBNAIL}) — 글 이미지와 같은 검사 */
    public Stored storeThumbnailByOperator(long operatorId, byte[] data) {
        return store(POST_THUMB_PREFIX, data, "operatorId=" + operatorId,
                (id, key, p, now) -> FileEntity.pendingPostThumbnailByOperator(id, key, p.format().contentType(), p.data().length, p.width(), p.height(), operatorId, now));
    }

    private Stored store(String prefix, byte[] data, String who, Ledger ledger) {
        PostImageProcessor.Processed processed = processor.process(data);
        String token = token();
        String key = prefix + MONTH.format(Instant.now()) + "/" + token + "." + processed.format().extension();
        Optional<PostThumbnailMaker.Thumbnail> thumb = thumbnails.make(processed.data(), "key=" + key);
        String thumbKey = thumb.map(t -> thumbnailKeyOf(key, t.format())).orElse(null);
        storage.put(key, processed.data());
        if (thumb.isPresent()) {
            try {
                storage.put(thumbKey, thumb.get().data());
            } catch (RuntimeException e) {
                deleteQuietly(key); // 썸네일을 쓰지 못하면 원본도 남기지 않는다 — 지금과 같은 503
                throw e;
            }
        }
        long fileId = ids.nextId();
        try {
            tx.executeWithoutResult(status -> {
                FileEntity file = ledger.create(fileId, key, processed, Instant.now());
                file.recordThumbnail(thumbKey);
                files.persist(file);
            });
        } catch (RuntimeException e) {
            deleteQuietly(key); // 장부에 없는 파일이 남지 않게
            if (thumbKey != null) {
                deleteQuietly(thumbKey);
            }
            throw e;
        }
        log.info("글 이미지를 올렸다 fileId={} prefix={} bytes={} thumbnail={} {}", fileId, prefix, processed.data().length, thumbKey != null, who);
        String url = storage.publicUrl(key);
        return new Stored(fileId, url, processed.width(), processed.height(), thumbKey == null ? url : storage.publicUrl(thumbKey));
    }

    /** 원본 키의 짝 썸네일 키 — {@code post-image/202611/abcd….png} → {@code post-image/202611/abcd…-t.jpg} */
    public static String thumbnailKeyOf(String storageKey, ImageFormat thumbnailFormat) {
        int dot = storageKey.lastIndexOf('.');
        int slash = storageKey.lastIndexOf('/');
        String base = dot > slash ? storageKey.substring(0, dot) : storageKey;
        return base + THUMB_SUFFIX + thumbnailFormat.extension();
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
