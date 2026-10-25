package com.community.nicknameimage.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 적용 중인 그림이 아니라 해제할 수 없음(409 {@code NICKNAME_IMAGE_NOT_ACTIVE}) — 이미 처리됐거나 다른 운영자가 먼저 처리했다 */
public class NicknameImageNotActiveException extends CommonException {

    public NicknameImageNotActiveException() {
        super(HttpStatus.CONFLICT, "NICKNAME_IMAGE_NOT_ACTIVE", new MessageRef("error.NICKNAME_IMAGE_NOT_ACTIVE"));
    }
}
