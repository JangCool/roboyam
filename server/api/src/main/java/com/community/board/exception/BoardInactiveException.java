package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 꺼진 게시판(403) — 메뉴·목록에는 없고 주소로 들어오면 이 오류 */
public class BoardInactiveException extends CommonException {

    public BoardInactiveException() {
        super(HttpStatus.FORBIDDEN, "BOARD_INACTIVE", new MessageRef("error.BOARD_INACTIVE"));
    }
}
