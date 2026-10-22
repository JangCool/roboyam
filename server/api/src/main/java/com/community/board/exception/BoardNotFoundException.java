package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없는 게시판(404) */
public class BoardNotFoundException extends CommonException {

    public BoardNotFoundException() {
        super(HttpStatus.NOT_FOUND, "BOARD_NOT_FOUND", new MessageRef("error.BOARD_NOT_FOUND"));
    }
}
