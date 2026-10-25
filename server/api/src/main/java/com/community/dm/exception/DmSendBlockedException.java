package com.community.dm.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 쪽지를 보낼 수 없음 — 코드는 user-web {@code useDmApi.ts} 머리말 그대로: {@code 400 DM_SELF} · {@code 403 EMAIL_NOT_VERIFIED} · {@code 403 ACCOUNT_RESTRICTED} ·
 * {@code 403 BUSINESS_ACCOUNT_RESTRICTED} · {@code 403 DM_NOT_PERMITTED}(회원 역할에 {@code message.send} 가 없음 — 로보얌이 더한 것) · {@code 403 DM_SENDER_TOO_NEW} ·
 * {@code 403 DM_UNAVAILABLE} · {@code 403 DM_RECIPIENT_REFUSES} · {@code 400 DM_CONTACT_NOT_ALLOWED}. 문구 키는 {@code dm.error.<코드>}(게시판 글쓰기의 같은 코드와 문구가 다르다).
 */
public class DmSendBlockedException extends CommonException {

    private DmSendBlockedException(HttpStatus status, String code, Object... args) {
        super(status, code, new MessageRef("dm.error." + code, args));
    }

    public static DmSendBlockedException self() {
        return new DmSendBlockedException(HttpStatus.BAD_REQUEST, "DM_SELF");
    }

    public static DmSendBlockedException contactNotAllowed() {
        return new DmSendBlockedException(HttpStatus.BAD_REQUEST, "DM_CONTACT_NOT_ALLOWED");
    }

    /** 403 — 보낼 수 없는 이유 코드 그대로 */
    public static DmSendBlockedException forbidden(String code, Object... args) {
        return new DmSendBlockedException(HttpStatus.FORBIDDEN, code, args);
    }
}
