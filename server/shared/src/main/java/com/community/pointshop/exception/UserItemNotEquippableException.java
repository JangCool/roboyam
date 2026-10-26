package com.community.pointshop.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 장착할 수 없는 아이템(소모형·아바타·회수됨·자리와 종류가 다름) */
public class UserItemNotEquippableException extends CommonException {

    public UserItemNotEquippableException() {
        super(HttpStatus.CONFLICT, "USER_ITEM_NOT_EQUIPPABLE", new MessageRef("error.USER_ITEM_NOT_EQUIPPABLE"));
    }
}
