package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 글이 있는 말머리를 지우려 했다(409) — 대신 끈다 */
public class BoardPrefixInUseException extends CommonException {

    public BoardPrefixInUseException() {
        super(HttpStatus.CONFLICT, "PREFIX_IN_USE", new MessageRef("error.PREFIX_IN_USE"), Map.of(), null);
    }
}
