package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 비공개 소모임 글을 멤버가 아닌 사람(비회원 포함)이 열었다(403) — 코드 {@code GROUP_POST_PRIVATE}. 제목·본문은 응답에 없다 */
public class GroupPostPrivateException extends CommonException {

    public GroupPostPrivateException() {
        super(HttpStatus.FORBIDDEN, "GROUP_POST_PRIVATE", new MessageRef("error.GROUP_POST_PRIVATE"));
    }
}
