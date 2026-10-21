package com.community.adminauth.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 비밀번호 재설정 승인번호를 쓸 수 없다(400, 하위 예외마다 {@code CERT_CODE_*} 코드). 화면은 코드 앞머리 {@code CERT_CODE_} 로 묶어 승인번호 칸 아래에 서버 문구를 보인다.
 * 틀린 횟수는 오류로 끝나는 요청에서도 남아야 하므로 서비스가 이 예외에는 롤백하지 않는다({@code noRollbackFor}).
 */
public abstract class CertCodeException extends CommonException {

    protected CertCodeException(String errorCode, MessageRef message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }
}
