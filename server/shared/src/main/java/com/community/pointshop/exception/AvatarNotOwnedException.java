package com.community.pointshop.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 사지 않은(또는 환불한) 유료 아바타 */
public class AvatarNotOwnedException extends CommonException {

    public AvatarNotOwnedException() {
        super(HttpStatus.CONFLICT, "AVATAR_NOT_OWNED", new MessageRef("error.AVATAR_NOT_OWNED"));
    }
}
