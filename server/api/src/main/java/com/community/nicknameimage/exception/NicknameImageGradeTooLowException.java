package com.community.nicknameimage.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 닉네임 그림을 신청할 자격(회원 역할 권한 {@code nickname-image.request})이 없음(403 {@code NICKNAME_IMAGE_GRADE_TOO_LOW}) — 문구에 필요한 등급 이름 */
public class NicknameImageGradeTooLowException extends CommonException {

    public NicknameImageGradeTooLowException(String requiredGradeLabel) {
        super(HttpStatus.FORBIDDEN, "NICKNAME_IMAGE_GRADE_TOO_LOW", new MessageRef("error.NICKNAME_IMAGE_GRADE_TOO_LOW", requiredGradeLabel));
    }
}
