package com.community.emoticon.service;

import com.community.emoticon.entity.EmoticonImageKind;
import com.community.emoticon.exception.EmoticonImageInvalidException;
import com.community.emoticon.exception.EmoticonUploadLimitException;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;
import com.community.emoticon.repository.master.EmoticonImageStore;
import com.community.emoticon.repository.master.EmoticonImageStore.ImageRow;
import com.community.media.service.ImageStorage;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 로보티콘 그림 올리기 — 작가(공개 사이트 {@code POST /api/me/emoticon-studio/images})와 운영자(관리자 {@code POST /api/admin/emoticons/uploads[/cover]})가 같이 쓴다.
 *
 * <p>순서: (작가) 하루 한도({@code 429 EMOTICON_UPLOAD_LIMIT}) → 검사({@link EmoticonImageValidator} — 트랜잭션 밖, 그림 처리 동안 DB 연결을 붙잡지 않는다) → 저장소 쓰기
 * → 장부 행({@code emoticon_images}) → 실패하면 쓴 파일을 지운다(사이트 이미지·글 이미지와 같은 차례 — {@code _backend-core.md} 「이미지·파일」).
 * 저장소 키는 {@code emoticons/{UTC 년월}/{무작위 32자}.{확장자}} — 주소를 짐작할 수 없고, 한 번 쓴 파일은 고치지 않는다.
 *
 * <p>올리기만 하고 팩에 넣지 않은 그림은 지금 지우지 않는다(정리 배치는 다음 단계 — 계약 「S1 구현 결과」).
 */
@Service
public class EmoticonImageService {

    private static final Logger log = LoggerFactory.getLogger(EmoticonImageService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneOffset.UTC);
    /** 작가 한 사람이 24시간에 올릴 수 있는 그림 수 */
    public static final int DAILY_LIMIT = 300;

    private final EmoticonImageValidator validator;
    private final EmoticonImageStore images;
    private final ImageStorage storage;
    private final SnowflakeIdGenerator ids;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    public EmoticonImageService(EmoticonImageValidator validator, EmoticonImageStore images, ImageStorage storage, SnowflakeIdGenerator ids, DataSource dataSource,
                                PlatformTransactionManager txManager, ObjectProvider<Clock> clocks) {
        this.validator = validator;
        this.images = images;
        this.storage = storage;
        this.ids = ids;
        this.jdbc = new JdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(txManager);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /**
     * 작가 올리기의 multipart 입구 — {@code kind} 는 {@code EMOTICON}(빠지면)·{@code COVER}(그 밖 {@code 400 fields.kind}), 파일이 없거나 비면 {@code 400 fields.file = TYPE}
     * (대표는 {@code COVER_TYPE}).
     */
    public ImageRow uploadByMember(long userId, MultipartFile file, String kindText) {
        EmoticonImageKind kind = kindOf(kindText);
        return uploadByMember(userId, bytesOf(file, kind, "file"), kind);
    }

    /** 운영자 올리기의 multipart 입구 — 로보티콘 칸 {@code file} · 대표 칸 {@code cover} */
    public ImageRow uploadByOperator(long operatorId, MultipartFile file, EmoticonImageKind kind) {
        return uploadByOperator(operatorId, bytesOf(file, kind, kind == EmoticonImageKind.COVER ? "cover" : "file"), kind);
    }

    private static EmoticonImageKind kindOf(String text) {
        if (text == null || text.isBlank() || "EMOTICON".equals(text)) {
            return EmoticonImageKind.EMOTICON;
        }
        if ("COVER".equals(text)) {
            return EmoticonImageKind.COVER;
        }
        throw ValidationException.of("kind", new MessageRef("emoticon.image.kind"));
    }

    private static byte[] bytesOf(MultipartFile file, EmoticonImageKind kind, String field) {
        String reason = kind == EmoticonImageKind.COVER ? "COVER_TYPE" : "TYPE";
        if (file == null || file.isEmpty()) {
            throw new EmoticonImageInvalidException(field, reason, new MessageRef("emoticon.image.empty"));
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new EmoticonImageInvalidException(field, reason, new MessageRef("emoticon.image." + reason));
        }
    }

    /** 작가가 올린다 — GIF 무한 반복이 아니면 막는다(user-web 목업과 같다). 오류 칸은 {@code file}(대표 이미지도) */
    public ImageRow uploadByMember(long userId, byte[] data, EmoticonImageKind kind) {
        Instant since = clock.instant().minus(Duration.ofDays(1));
        Integer recent = tx.execute(s -> jdbc.queryForObject("SELECT count(*) FROM emoticon_images WHERE uploaded_by_user = ? AND created_at >= ?", Integer.class, userId,
                Timestamp.from(since)));
        if (recent != null && recent >= DAILY_LIMIT) {
            throw new EmoticonUploadLimitException(DAILY_LIMIT);
        }
        EmoticonImageValidator.Checked checked = validator.check(data, kind, true, "file");
        return store(checked, userId, null);
    }

    /** 운영자가 올린다(운영 팩) — GIF 한 번만 재생은 받아 둔다(admin-web 목업 — 화면이 경고). 오류 칸은 로보티콘 {@code file} · 대표 {@code cover} */
    public ImageRow uploadByOperator(long operatorId, byte[] data, EmoticonImageKind kind) {
        EmoticonImageValidator.Checked checked = validator.check(data, kind, false, kind == EmoticonImageKind.COVER ? "cover" : "file");
        return store(checked, null, operatorId);
    }

    private ImageRow store(EmoticonImageValidator.Checked checked, Long userId, Long operatorId) {
        Instant now = clock.instant();
        String key = "emoticons/" + MONTH.format(now) + "/" + token() + "." + checked.format().extension();
        storage.put(key, checked.data());
        ImageRow row = new ImageRow(ids.nextId(), checked.kind(), checked.format(), checked.width(), checked.height(), checked.data().length, checked.frames(),
                checked.loopForever(), checked.contentHash(), key, null, userId, operatorId, now);
        try {
            tx.executeWithoutResult(s -> images.insert(row));
        } catch (RuntimeException e) {
            deleteQuietly(key); // 장부에 없는 파일이 남지 않게
            throw e;
        }
        log.info("로보티콘 그림을 올렸다 imageId={} kind={} bytes={} userId={} operatorId={}", row.id(), row.kind(), row.bytes(), userId, operatorId);
        return row;
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("장부에 넣지 못한 로보티콘 그림 파일을 지우지 못했다 key={}", key, e);
        }
    }

    private static String token() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
