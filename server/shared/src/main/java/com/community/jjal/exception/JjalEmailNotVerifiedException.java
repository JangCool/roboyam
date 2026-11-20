package com.community.jjal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 403 — 이메일 인증 전이라 짤을 올리거나 담을 수 없다. 코드는 게시판 쓰기와 같은 {@code EMAIL_NOT_VERIFIED}(화면이 코드로 가른다), 문구만 짤용 */
public class JjalEmailNotVerifiedException extends CommonException {

    public JjalEmailNotVerifiedException() {
        super(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", new MessageRef("error.EMAIL_NOT_VERIFIED.jjal"));
    }
}
