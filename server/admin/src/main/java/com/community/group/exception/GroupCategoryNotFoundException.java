package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 소모임 분류가 없다(404) */
public class GroupCategoryNotFoundException extends CommonException {

    public GroupCategoryNotFoundException() {
        super(HttpStatus.NOT_FOUND, "GROUP_CATEGORY_NOT_FOUND", new MessageRef("error.GROUP_CATEGORY_NOT_FOUND"));
    }
}
