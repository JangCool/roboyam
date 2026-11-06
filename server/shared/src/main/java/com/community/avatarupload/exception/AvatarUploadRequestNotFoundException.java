package com.community.avatarupload.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 {@code AVATAR_UPLOAD_REQUEST_NOT_FOUND} — 취소할 대기 신청이 없다 */
public class AvatarUploadRequestNotFoundException extends CommonException {

    public AvatarUploadRequestNotFoundException() {
        super(HttpStatus.NOT_FOUND, "AVATAR_UPLOAD_REQUEST_NOT_FOUND", new MessageRef("error.AVATAR_UPLOAD_REQUEST_NOT_FOUND"));
    }
}
