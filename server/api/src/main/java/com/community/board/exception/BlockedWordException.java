package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 금지어가 들어 있음(400, 보안 설정이 거부 방식일 때) — 문구에 걸린 금지어(운영자가 정한 단어)를 싣는다 */
public class BlockedWordException extends CommonException {

    public BlockedWordException(String word) {
        super(HttpStatus.BAD_REQUEST, "BLOCKED_WORD", new MessageRef("error.BLOCKED_WORD", word));
    }
}
