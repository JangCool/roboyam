package com.community.pointshop.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 없거나 내 것이 아닌 보유 아이템 */
public class UserItemNotFoundException extends CommonException {

    public UserItemNotFoundException() {
        super(HttpStatus.NOT_FOUND, "USER_ITEM_NOT_FOUND", new MessageRef("error.USER_ITEM_NOT_FOUND"));
    }
}
