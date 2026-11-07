package com.community.avatarupload.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 403 — 이메일 인증 전({@code EMAIL_NOT_VERIFIED})·정지 중({@code ACCOUNT_RESTRICTED})이라 올리기·취소·지우기를 할 수 없다(닉네임 이미지와 같다). 문구 키 {@code avatarUpload.error.<코드>} */
public class AvatarUploadBlockedException extends CommonException {

    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";
    public static final String ACCOUNT_RESTRICTED = "ACCOUNT_RESTRICTED";

    public AvatarUploadBlockedException(String code) {
        super(HttpStatus.FORBIDDEN, code, new MessageRef("avatarUpload.error." + code));
    }
}
