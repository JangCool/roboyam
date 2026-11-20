package com.community.jjal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 422 — 짤을 끈 게시판(본문 {@code postJjalAllowed} · 댓글 {@code commentJjalAllowed})에 새로 넣은 짤 토큰이 있다(이미 있던 짤은 그대로 둔다) */
public class JjalNotAllowedException extends CommonException {

    public JjalNotAllowedException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "JJAL_NOT_ALLOWED", new MessageRef("error.JJAL_NOT_ALLOWED"));
    }
}
