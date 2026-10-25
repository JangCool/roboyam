package com.community.debate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 「입장 바꾸기 허용」이 꺼진 토론에서 이미 고른 입장을 다른 값으로 바꾸려 했다(409) — 처음 고르기·같은 값 다시 보내기는 여기에 걸리지 않는다 */
public class StanceChangeLockedException extends CommonException {

    public StanceChangeLockedException() {
        super(HttpStatus.CONFLICT, "STANCE_CHANGE_LOCKED", new MessageRef("error.STANCE_CHANGE_LOCKED"));
    }
}
