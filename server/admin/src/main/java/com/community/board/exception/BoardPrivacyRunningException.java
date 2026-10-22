package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 개인정보 보유기간 배치(또는 다른 지금 실행)가 도는 중이다(409) */
public class BoardPrivacyRunningException extends CommonException {

    public BoardPrivacyRunningException() {
        super(HttpStatus.CONFLICT, "BOARD_PRIVACY_RUNNING", new MessageRef("error.BOARD_PRIVACY_RUNNING"), Map.of(), null);
    }
}
