package com.community.block.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없는 회원·탈퇴한 회원·형식이 틀린 회원 ID(404 {@code PROFILE_NOT_FOUND}) — 차단·메모·쪽지가 같이 쓴다 */
public class MemberProfileNotFoundException extends CommonException {

    public MemberProfileNotFoundException() {
        super(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", new MessageRef("error.PROFILE_NOT_FOUND"));
    }
}
