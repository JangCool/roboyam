package com.community.point.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 403 — 출석할 자격이 없다. 코드는 까닭 그대로({@link #ACCOUNT_RESTRICTED} 정지·영구 정지 · {@link #EMAIL_NOT_VERIFIED} 이메일 인증 전 — 포인트 상점 구매·글쓰기와 같은 코드).
 * 문구 키는 {@code attendance.blocked.<코드>}.
 */
public class AttendanceBlockedException extends CommonException {

    public static final String ACCOUNT_RESTRICTED = "ACCOUNT_RESTRICTED";
    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";

    public AttendanceBlockedException(String reason) {
        super(HttpStatus.FORBIDDEN, reason, new MessageRef("attendance.blocked." + reason));
    }
}
