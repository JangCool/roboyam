package com.community.adultboard.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 400 {@code ADULT_BOARD_PIN_WRONG} — 핀번호가 틀렸다. 오류 본문 최상위 {@code attemptsLeft} = 잠기기 전까지 남은 횟수(1 이상 — 한도에 닿은 시도는
 * {@link AdultBoardPinLockedException} 이다). 칸 이름은 화면·계약(adult-board-pin-contract)이 정한 {@code attemptsLeft} 다.
 */
public class AdultBoardPinWrongException extends CommonException {

    public AdultBoardPinWrongException(int attemptsLeft) {
        super(HttpStatus.BAD_REQUEST, "ADULT_BOARD_PIN_WRONG", new MessageRef("error.ADULT_BOARD_PIN_WRONG"));
        extra("attemptsLeft", attemptsLeft);
    }
}
