package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이 분류의 게시판을 쓰는 소모임(열림·비활성·대기)이 있어 지울 수 없다(409) — 오류 본문 최상위 {@code groupCount}(화면 {@code ApiError.extras.groupCount}) */
public class GroupCategoryNotEmptyException extends CommonException {

    public GroupCategoryNotEmptyException(long groupCount) {
        super(HttpStatus.CONFLICT, "GROUP_CATEGORY_NOT_EMPTY", new MessageRef("error.GROUP_CATEGORY_NOT_EMPTY"));
        extra("groupCount", groupCount);
    }
}
