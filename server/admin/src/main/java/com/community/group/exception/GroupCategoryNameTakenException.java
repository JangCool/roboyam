package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 같은 이름(공백·대소문자 무시)의 소모임 분류가 있다(409) */
public class GroupCategoryNameTakenException extends CommonException {

    public GroupCategoryNameTakenException() {
        super(HttpStatus.CONFLICT, "GROUP_CATEGORY_NAME_TAKEN", new MessageRef("error.GROUP_CATEGORY_NAME_TAKEN"));
    }
}
