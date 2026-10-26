package com.community.pointshop.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 카탈로그에 없는 아바타 */
public class AvatarNotFoundException extends CommonException {

    public AvatarNotFoundException() {
        super(HttpStatus.NOT_FOUND, "AVATAR_NOT_FOUND", new MessageRef("error.AVATAR_NOT_FOUND"));
    }
}
