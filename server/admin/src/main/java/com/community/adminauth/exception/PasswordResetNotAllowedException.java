package com.community.adminauth.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 승인번호는 맞지만 그 사이 계정이 로그인할 수 없는 상태(잠금·미승인·만료·사용 중지·기간 밖)가 됐다(403 {@code PASSWORD_RESET_NOT_ALLOWED}).
 * 맞는 승인번호는 그 계정으로 메일이 갔다는 뜻이라(승인번호는 로그인할 수 있는 계정에만 보낸다) 이 응답이 계정 존재를 새게 하지 않는다.
 */
public class PasswordResetNotAllowedException extends CommonException {

    public PasswordResetNotAllowedException() {
        super(HttpStatus.FORBIDDEN, "PASSWORD_RESET_NOT_ALLOWED", new MessageRef("error.PASSWORD_RESET_NOT_ALLOWED"));
    }
}
