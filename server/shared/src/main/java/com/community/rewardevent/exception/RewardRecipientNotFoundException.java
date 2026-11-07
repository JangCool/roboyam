package com.community.rewardevent.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 이 이벤트의 받는 사람이 아니다 */
public class RewardRecipientNotFoundException extends CommonException {

    public RewardRecipientNotFoundException() {
        super(HttpStatus.NOT_FOUND, "REWARD_RECIPIENT_NOT_FOUND", new MessageRef("error.REWARD_RECIPIENT_NOT_FOUND"));
    }
}
