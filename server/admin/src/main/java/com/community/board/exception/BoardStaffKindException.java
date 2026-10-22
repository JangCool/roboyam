package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 이 종류(핫딜·직접홍보·소모임)의 게시판에는 매니저·스텝을 지정할 수 없다(409) */
public class BoardStaffKindException extends CommonException {

    public BoardStaffKindException() {
        super(HttpStatus.CONFLICT, "BOARD_STAFF_KIND", new MessageRef("error.BOARD_STAFF_KIND"), Map.of(), null);
    }
}
