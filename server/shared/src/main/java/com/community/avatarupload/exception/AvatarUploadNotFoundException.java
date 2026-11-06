package com.community.avatarupload.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 {@code AVATAR_UPLOAD_NOT_FOUND} — 회원: 지울 「내가 올린 아바타」가 없다. 운영자: 없는·형식이 틀린 신청 ID */
public class AvatarUploadNotFoundException extends CommonException {

    public AvatarUploadNotFoundException() {
        super(HttpStatus.NOT_FOUND, "AVATAR_UPLOAD_NOT_FOUND", new MessageRef("error.AVATAR_UPLOAD_NOT_FOUND"));
    }
}
