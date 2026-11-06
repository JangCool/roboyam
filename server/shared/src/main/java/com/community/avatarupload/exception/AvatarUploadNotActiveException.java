package com.community.avatarupload.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 {@code AVATAR_UPLOAD_NOT_ACTIVE} — 해제는 회원의 지금 「내가 올린 아바타」만 */
public class AvatarUploadNotActiveException extends CommonException {

    public AvatarUploadNotActiveException() {
        super(HttpStatus.CONFLICT, "AVATAR_UPLOAD_NOT_ACTIVE", new MessageRef("error.AVATAR_UPLOAD_NOT_ACTIVE"));
    }
}
