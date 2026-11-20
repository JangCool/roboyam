package com.community.jjal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 신고로 내려간 짤(담기 · 글·댓글에 새로 넣기 · 같은 해시 다시 올리기). 최상위 {@code jjalId} = 그 짤 공개 ID(올리기는 싣지 않는다) */
public class JjalBlockedException extends CommonException {

    public JjalBlockedException(String jjalId) {
        super(HttpStatus.CONFLICT, "JJAL_BLOCKED", new MessageRef("error.JJAL_BLOCKED"));
        if (jjalId != null) {
            extra("jjalId", jjalId);
        }
    }
}
