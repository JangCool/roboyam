package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 개인정보 보유기간 적용(전체 스위치)이 꺼져 있어 지금 실행할 수 없다(409) */
public class BoardPrivacyDisabledException extends CommonException {

    public BoardPrivacyDisabledException() {
        super(HttpStatus.CONFLICT, "BOARD_PRIVACY_DISABLED", new MessageRef("error.BOARD_PRIVACY_DISABLED"), Map.of(), null);
    }
}
