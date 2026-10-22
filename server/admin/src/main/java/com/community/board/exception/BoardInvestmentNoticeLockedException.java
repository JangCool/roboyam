package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 투자 고지는 켠 뒤 끌 수 없다(409, fields.investmentNotice) */
public class BoardInvestmentNoticeLockedException extends CommonException {

    public BoardInvestmentNoticeLockedException() {
        super(HttpStatus.CONFLICT, "BOARD_INVESTMENT_NOTICE_LOCKED", new MessageRef("error.BOARD_INVESTMENT_NOTICE_LOCKED"), Map.of("investmentNotice", new MessageRef("error.BOARD_INVESTMENT_NOTICE_LOCKED")), null);
    }
}
