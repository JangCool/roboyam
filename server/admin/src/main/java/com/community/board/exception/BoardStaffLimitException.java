package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 이 게시판의 매니저(또는 스텝) 인원이 찼다(409) */
public class BoardStaffLimitException extends CommonException {

    public BoardStaffLimitException(boolean manager, int max) {
        super(HttpStatus.CONFLICT, "BOARD_STAFF_LIMIT", new MessageRef(manager ? "error.BOARD_STAFF_LIMIT.manager" : "error.BOARD_STAFF_LIMIT.staff", String.valueOf(max)), Map.of(), null);
    }
}
