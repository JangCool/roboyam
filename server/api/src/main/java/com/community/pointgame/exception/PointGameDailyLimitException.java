package com.community.pointgame.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 오늘(한국 시간) 룰렛을 하루 횟수만큼 다 돌렸다(point-games-contract 「룰렛」) */
public class PointGameDailyLimitException extends CommonException {

    public PointGameDailyLimitException() {
        super(HttpStatus.CONFLICT, "POINT_GAME_DAILY_LIMIT", new MessageRef("error.POINT_GAME_DAILY_LIMIT"));
    }
}
