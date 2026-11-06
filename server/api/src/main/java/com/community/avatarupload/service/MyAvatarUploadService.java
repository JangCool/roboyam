package com.community.avatarupload.service;

import com.community.avatarupload.dto.response.MyAvatarUploadView;
import com.community.media.service.ImageStorage;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 내 아바타 올리기(공개 사이트) — 파일 검사·변환·저장소 쓰기(트랜잭션 밖)를 하고 DB 는 shared {@link AvatarUploadService} 가 바꾼다. commerce-motion-avatar-contract 「API 계약」.
 *
 * <p><b>올리기 순서</b>: 이메일 인증 전 {@code 403 EMAIL_NOT_VERIFIED} → 정지 {@code 403 ACCOUNT_RESTRICTED} → 변경권 {@code 409 AVATAR_UPLOAD_TICKET_REQUIRED}(파일 처리 전) →
 * 파일(빈 파일·300KB·형식 {@code 400 fields.file}) → 속도(10초·하루 10번 {@code 429 RATE_LIMITED}) → 처리(정사각형·크기·장면 {@code 400 fields.file}) → 저장소 쓰기 → 트랜잭션
 * (프로필 행 잠금 → 대기 신청 대체(예약된 장을 넘겨받는다) 또는 한 장 예약 → 반려 파일 정리 → 새 대기 신청 → 운영자 알림) → 커밋 뒤 예전 파일 삭제(실패하면 새 파일 삭제).
 * <p><b>파일 주소</b>: 저장소 키 {@code avatar-upload/{무작위 32자}/image.{확장자}}(+ 움직이는 GIF 의 {@code still.png}) — 닉네임 이미지와 같이 심사 전에도 짐작할 수 없는 공개 주소다.
 */
@Service
public class MyAvatarUploadService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AvatarUploadService uploads;
    private final AvatarUploadProcessor processor;
    private final ImageStorage storage;
    private final SnowflakeIdGenerator ids;

    public MyAvatarUploadService(AvatarUploadService uploads, AvatarUploadProcessor processor, ImageStorage storage, SnowflakeIdGenerator ids) {
        this.uploads = uploads;
        this.processor = processor;
        this.storage = storage;
        this.ids = ids;
    }

    public MyAvatarUploadView status(long userId) {
        return uploads.status(userId);
    }

    public MyAvatarUploadView apply(long userId, MultipartFile file) {
        uploads.guard(userId);
        uploads.requireTicket(userId);
        byte[] data;
        try {
            data = file == null ? null : file.getBytes();
        } catch (IOException ex) {
            throw ValidationException.of("file", new MessageRef("avatarUpload.file.corrupt"));
        }
        var format = processor.precheck(data);
        uploads.rateLimit(userId);
        AvatarUploadProcessor.Processed processed = processor.process(data, format);

        String folder = AvatarUploadRules.KEY_PREFIX + token();
        String imageKey = folder + "/image." + processed.format().extension();
        String stillKey = processed.still() == null ? null : folder + "/still.png";
        storage.put(imageKey, processed.data());
        List<String> stale;
        try {
            if (stillKey != null) {
                storage.put(stillKey, processed.still());
            }
            stale = uploads.submit(userId, new AvatarUploadService.NewUpload(ids.nextId(), imageKey, stillKey, processed.format().contentType(), data.length,
                    processed.width(), processed.height(), processed.frames()));
        } catch (RuntimeException ex) {
            uploads.deleteQuietly(imageKey);
            uploads.deleteQuietly(stillKey);
            throw ex;
        }
        stale.forEach(uploads::deleteQuietly);
        return uploads.status(userId);
    }

    public void cancelRequest(long userId) {
        uploads.cancel(userId);
    }

    public void remove(long userId) {
        uploads.remove(userId);
    }

    private static String token() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
