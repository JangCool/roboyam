package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 공개 소모임에서 모임장·매니저가 멤버 전용으로 돌린 글이다(409) — 코드 {@code GROUP_POST_MEMBERS_ONLY} */
public class GroupPostMembersOnlyException extends CommonException {

    public GroupPostMembersOnlyException() {
        super(HttpStatus.CONFLICT, "GROUP_POST_MEMBERS_ONLY", new MessageRef("error.GROUP_POST_MEMBERS_ONLY"));
    }
}
