package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 같은 이름(대소문자 무시)의 게시판이 있다(409, fields.name) */
public class BoardNameDuplicateException extends CommonException {

    public BoardNameDuplicateException() {
        super(HttpStatus.CONFLICT, "BOARD_NAME_DUPLICATE", new MessageRef("error.BOARD_NAME_DUPLICATE"), Map.of("name", new MessageRef("error.BOARD_NAME_DUPLICATE")), null);
    }
}
