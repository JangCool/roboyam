package com.community.nicknameimage.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이메일 인증 전({@code EMAIL_NOT_VERIFIED})·정지 중({@code ACCOUNT_RESTRICTED})이라 올리기·취소·해제를 할 수 없음(403). 문구 키는 {@code nicknameImage.error.<코드>} */
public class NicknameImageBlockedException extends CommonException {

    public NicknameImageBlockedException(String code) {
        super(HttpStatus.FORBIDDEN, code, new MessageRef("nicknameImage.error." + code));
    }
}
