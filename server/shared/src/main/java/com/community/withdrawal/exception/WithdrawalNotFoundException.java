package com.community.withdrawal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 그 탈퇴 신청 기록이 없다(404 {@code WITHDRAWAL_NOT_FOUND}) — 관리자 상세·회원별 최근 기록(신청한 적 없음) */
public class WithdrawalNotFoundException extends CommonException {

    public WithdrawalNotFoundException() {
        super(HttpStatus.NOT_FOUND, "WITHDRAWAL_NOT_FOUND", new MessageRef("error.WITHDRAWAL_NOT_FOUND"));
    }
}
