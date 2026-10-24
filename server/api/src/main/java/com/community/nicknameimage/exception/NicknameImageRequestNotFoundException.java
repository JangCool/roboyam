package com.community.nicknameimage.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 취소할 대기 신청이 없음(404 {@code NICKNAME_IMAGE_REQUEST_NOT_FOUND}) — 이미 처리됐거나 취소됐다 */
public class NicknameImageRequestNotFoundException extends CommonException {

    public NicknameImageRequestNotFoundException() {
        super(HttpStatus.NOT_FOUND, "NICKNAME_IMAGE_REQUEST_NOT_FOUND", new MessageRef("error.NICKNAME_IMAGE_REQUEST_NOT_FOUND"));
    }
}
