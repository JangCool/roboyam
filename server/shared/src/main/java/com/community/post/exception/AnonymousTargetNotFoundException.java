package com.community.post.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 「이 글/댓글 작성자」로 가리킨 글·댓글이 없다(형식이 틀림 · 없음 · 파기됨 · 비회원 글) — anonymous-board-contract 「API 계약」 */
public class AnonymousTargetNotFoundException extends CommonException {

    public AnonymousTargetNotFoundException() {
        super(HttpStatus.NOT_FOUND, "ANONYMOUS_TARGET_NOT_FOUND", new MessageRef("error.ANONYMOUS_TARGET_NOT_FOUND"));
    }
}
