package com.community.withdrawal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 탈퇴 진행 중(409 {@code WITHDRAWAL_ALREADY_SCHEDULED}) */
public class WithdrawalAlreadyScheduledException extends CommonException {

    public WithdrawalAlreadyScheduledException() {
        super(HttpStatus.CONFLICT, "WITHDRAWAL_ALREADY_SCHEDULED", new MessageRef("error.WITHDRAWAL_ALREADY_SCHEDULED"));
    }
}
