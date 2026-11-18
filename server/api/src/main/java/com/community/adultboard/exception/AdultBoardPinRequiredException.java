package com.community.adultboard.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * 403 {@code ADULT_BOARD_PIN_REQUIRED} — 성인 전용 게시판 접근을 켠 성인이지만 이 기기(로그인 세션)에서 핀번호를 아직 확인하지 않았거나 확인이 끝났다
 * (adult-board-pin-contract). 화면은 내용 자리에 핀 입력 화면을 보인다. 핀을 여러 번 틀려 잠긴 중이면 오류 본문 최상위 {@code lockedUntil}(ISO 시각)을 싣는다.
 */
public class AdultBoardPinRequiredException extends CommonException {

    /** @param lockedUntil 틀림 잠금이 끝나는 때(잠기지 않았으면 null) */
    public AdultBoardPinRequiredException(Instant lockedUntil) {
        super(HttpStatus.FORBIDDEN, "ADULT_BOARD_PIN_REQUIRED", new MessageRef("error.ADULT_BOARD_PIN_REQUIRED"));
        if (lockedUntil != null) {
            extra("lockedUntil", lockedUntil.toString());
        }
    }
}
