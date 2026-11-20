package com.community.jjal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 글·댓글에 새로 넣은 짤이 작성자의 지금 보관함에 없다. 최상위 {@code jjalId} = 본문 순서로 처음 걸린 짤 */
public class JjalNotInBoxException extends CommonException {

    public JjalNotInBoxException(String jjalId) {
        super(HttpStatus.CONFLICT, "JJAL_NOT_IN_BOX", new MessageRef("error.JJAL_NOT_IN_BOX"));
        extra("jjalId", jjalId);
    }
}
