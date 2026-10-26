package com.community.pointshop.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 기간이 끝난 기간제 아이템은 장착할 수 없다 */
public class UserItemExpiredException extends CommonException {

    public UserItemExpiredException() {
        super(HttpStatus.CONFLICT, "USER_ITEM_EXPIRED", new MessageRef("error.USER_ITEM_EXPIRED"));
    }
}
