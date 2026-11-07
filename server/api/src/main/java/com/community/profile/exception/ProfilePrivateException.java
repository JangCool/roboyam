package com.community.profile.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 비공개 프로필의 작성글·댓글·소모임 목록을 본인이 아닌 사람(비회원 포함)이 열었다(403) — profile-privacy-contract 「API」 */
public class ProfilePrivateException extends CommonException {

    public ProfilePrivateException() {
        super(HttpStatus.FORBIDDEN, "PROFILE_PRIVATE", new MessageRef("error.PROFILE_PRIVATE"));
    }
}
