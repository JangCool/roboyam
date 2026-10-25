package com.community.nicknameimage.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 아직 다시 신청할 수 없음(409 {@code NICKNAME_IMAGE_REAPPLY_TOO_SOON}, 반려 72시간·운영 해제 7일) — {@code retryAfterSeconds} 가 남은 초.
 * 끝 시각({@code canReapplyAt})은 공통 오류 응답에 칸이 없어 싣지 않는다 — 같은 값이 {@code GET /api/me/nickname-image} 의 {@code reapplyAt} 에 있다.
 */
public class NicknameImageReapplyTooSoonException extends CommonException {

    public NicknameImageReapplyTooSoonException(long retryAfterSeconds) {
        super(HttpStatus.CONFLICT, "NICKNAME_IMAGE_REAPPLY_TOO_SOON", new MessageRef("error.NICKNAME_IMAGE_REAPPLY_TOO_SOON"), Map.of(), Math.max(1, retryAfterSeconds));
    }
}
