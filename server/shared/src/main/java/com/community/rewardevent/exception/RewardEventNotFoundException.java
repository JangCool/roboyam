package com.community.rewardevent.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 없는 보상 이벤트(형식이 틀린 번호 포함) */
public class RewardEventNotFoundException extends CommonException {

    public RewardEventNotFoundException() {
        super(HttpStatus.NOT_FOUND, "REWARD_EVENT_NOT_FOUND", new MessageRef("error.REWARD_EVENT_NOT_FOUND"));
    }
}
