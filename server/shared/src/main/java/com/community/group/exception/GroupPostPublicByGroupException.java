package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 공개 소모임의 글은 작성자가 비공개로 돌릴 수 없다(409 — 지우려면 삭제) — 코드 {@code GROUP_POST_PUBLIC_BY_GROUP} */
public class GroupPostPublicByGroupException extends CommonException {

    public GroupPostPublicByGroupException() {
        super(HttpStatus.CONFLICT, "GROUP_POST_PUBLIC_BY_GROUP", new MessageRef("error.GROUP_POST_PUBLIC_BY_GROUP"));
    }
}
