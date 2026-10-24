package com.community.debate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 아직 이 글에서 입장을 정하지 않은 회원이 입장을 바꾸려 한다(409) — 입장은 찬성·반대로 처음 의견을 쓸 때 정해진다 */
public class StanceNotSetException extends CommonException {

    public StanceNotSetException() {
        super(HttpStatus.CONFLICT, "STANCE_NOT_SET", new MessageRef("error.STANCE_NOT_SET"));
    }
}
