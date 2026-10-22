package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 사용 중인 고유 말머리가 한도를 넘는다(409, fields.prefixes) */
public class BoardPrefixLimitException extends CommonException {

    public BoardPrefixLimitException(int max) {
        super(HttpStatus.CONFLICT, "PREFIX_LIMIT", new MessageRef("error.PREFIX_LIMIT", String.valueOf(max)), Map.of("prefixes", new MessageRef("error.PREFIX_LIMIT", String.valueOf(max))), null);
    }
}
