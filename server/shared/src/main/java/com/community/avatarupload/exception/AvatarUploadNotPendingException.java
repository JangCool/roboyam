package com.community.avatarupload.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 {@code AVATAR_UPLOAD_NOT_PENDING} — 승인·반려는 심사 대기 신청만(이미 처리됐다) */
public class AvatarUploadNotPendingException extends CommonException {

    public AvatarUploadNotPendingException() {
        super(HttpStatus.CONFLICT, "AVATAR_UPLOAD_NOT_PENDING", new MessageRef("error.AVATAR_UPLOAD_NOT_PENDING"));
    }
}
