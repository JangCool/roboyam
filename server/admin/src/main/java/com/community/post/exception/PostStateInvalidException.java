package com.community.post.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 게시글 관리 — 글 상태가 그 조치를 허락하지 않는다(409 POST_STATE_INVALID). 예: 숨긴 글을 숨김 · 지운 글을 이동 · 숨기지 않은 글의 숨김 해제 */
public class PostStateInvalidException extends CommonException {

    public PostStateInvalidException() {
        super(HttpStatus.CONFLICT, "POST_STATE_INVALID", new MessageRef("error.POST_STATE_INVALID"));
    }
}
