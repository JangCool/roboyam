package com.community.post.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 게시글 관리 — 원문을 파기한 글은 복구할 수 없다(409 POST_PURGED) */
public class PostPurgedException extends CommonException {

    public PostPurgedException() {
        super(HttpStatus.CONFLICT, "POST_PURGED", new MessageRef("error.POST_PURGED"));
    }
}
