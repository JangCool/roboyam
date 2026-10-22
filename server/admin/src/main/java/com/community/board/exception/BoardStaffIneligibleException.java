package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 지정 자격이 없는 회원이다(409) */
public class BoardStaffIneligibleException extends CommonException {

    public BoardStaffIneligibleException() {
        super(HttpStatus.CONFLICT, "BOARD_STAFF_INELIGIBLE", new MessageRef("error.BOARD_STAFF_INELIGIBLE"), Map.of(), null);
    }
}
