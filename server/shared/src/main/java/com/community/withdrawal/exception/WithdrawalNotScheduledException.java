package com.community.withdrawal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 진행 중인 탈퇴 신청이 없다(409 {@code WITHDRAWAL_NOT_SCHEDULED}) — 취소할 것이 없다 */
public class WithdrawalNotScheduledException extends CommonException {

    public WithdrawalNotScheduledException() {
        super(HttpStatus.CONFLICT, "WITHDRAWAL_NOT_SCHEDULED", new MessageRef("error.WITHDRAWAL_NOT_SCHEDULED"));
    }
}
