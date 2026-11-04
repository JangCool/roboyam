package com.community.withdrawal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 탈퇴를 완료한 회원이라 다시 탈퇴시킬 수 없다(409 {@code MEMBER_ALREADY_WITHDRAWN}) — 운영자 강제 탈퇴·미리보기 */
public class MemberAlreadyWithdrawnException extends CommonException {

    public MemberAlreadyWithdrawnException() {
        super(HttpStatus.CONFLICT, "MEMBER_ALREADY_WITHDRAWN", new MessageRef("error.MEMBER_ALREADY_WITHDRAWN"));
    }
}
