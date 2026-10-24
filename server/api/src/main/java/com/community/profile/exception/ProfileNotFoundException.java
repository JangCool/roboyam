package com.community.profile.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없는 회원·탈퇴(삭제)한 회원·형식이 틀린 ID(404) — 셋을 가르지 않는다 */
public class ProfileNotFoundException extends CommonException {

    public ProfileNotFoundException() {
        super(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", new MessageRef("error.PROFILE_NOT_FOUND"));
    }
}
