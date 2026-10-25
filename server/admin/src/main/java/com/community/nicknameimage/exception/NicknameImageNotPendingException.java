package com.community.nicknameimage.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 대기 중인 신청이 아니라 승인·반려할 수 없음(409 {@code NICKNAME_IMAGE_NOT_PENDING}) — 이미 처리됐거나 다른 운영자가 먼저 처리했다 */
public class NicknameImageNotPendingException extends CommonException {

    public NicknameImageNotPendingException() {
        super(HttpStatus.CONFLICT, "NICKNAME_IMAGE_NOT_PENDING", new MessageRef("error.NICKNAME_IMAGE_NOT_PENDING"));
    }
}
