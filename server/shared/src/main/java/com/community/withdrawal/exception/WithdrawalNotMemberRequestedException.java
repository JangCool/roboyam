package com.community.withdrawal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 운영자 강제 탈퇴는 대리 취소할 수 없다(409 {@code WITHDRAWAL_NOT_MEMBER_REQUESTED}) — 대리 취소는 회원이 신청한 탈퇴만 */
public class WithdrawalNotMemberRequestedException extends CommonException {

    public WithdrawalNotMemberRequestedException() {
        super(HttpStatus.CONFLICT, "WITHDRAWAL_NOT_MEMBER_REQUESTED", new MessageRef("error.WITHDRAWAL_NOT_MEMBER_REQUESTED"));
    }
}
