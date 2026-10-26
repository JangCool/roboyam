package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 화면이 불러온 뒤 다른 관리자가 이 분류를 먼저 고쳤다(409) — 저장하지 않았다. 화면은 다시 불러온다 */
public class GroupCategoryStaleException extends CommonException {

    public GroupCategoryStaleException() {
        super(HttpStatus.CONFLICT, "GROUP_CATEGORY_STALE", new MessageRef("error.GROUP_CATEGORY_STALE"));
    }
}
