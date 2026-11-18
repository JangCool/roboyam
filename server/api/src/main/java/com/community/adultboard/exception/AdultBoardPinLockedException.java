package com.community.adultboard.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 423 {@code ADULT_BOARD_PIN_LOCKED} — 핀번호를 연속으로 틀려 잠겼다(회원 단위 — 기기를 바꿔도 잠겨 있다). 잠긴 동안은 맞는 핀번호도 받지 않는다.
 * {@code retryAfterSeconds}(+ {@code Retry-After} 헤더)와 오류 본문 최상위 {@code lockedUntil}(ISO 시각)을 싣는다.
 */
public class AdultBoardPinLockedException extends CommonException {

    public AdultBoardPinLockedException(Instant lockedUntil, long retryAfterSeconds) {
        super(HttpStatus.LOCKED, "ADULT_BOARD_PIN_LOCKED", new MessageRef("error.ADULT_BOARD_PIN_LOCKED"), Map.of(), retryAfterSeconds);
        extra("lockedUntil", lockedUntil.toString());
    }
}
