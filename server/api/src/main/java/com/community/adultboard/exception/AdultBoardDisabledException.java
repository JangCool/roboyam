package com.community.adultboard.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 {@code ADULT_BOARD_DISABLED} — 「19금 게시판 접근」이 꺼져 있는데 핀번호를 확인하거나 바꾸려 했다(먼저 켜야 한다) */
public class AdultBoardDisabledException extends CommonException {

    public AdultBoardDisabledException() {
        super(HttpStatus.CONFLICT, "ADULT_BOARD_DISABLED", new MessageRef("error.ADULT_BOARD_DISABLED"));
    }
}
