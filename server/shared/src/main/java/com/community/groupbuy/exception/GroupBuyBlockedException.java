package com.community.groupbuy.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 403 — 공구에 참여할 자격이 없다(commerce.md 10-5 「참여 자격」). 코드는 까닭 그대로({@link #ACCOUNT_RESTRICTED} 정지·영구 정지 · {@link #EMAIL_NOT_VERIFIED} ·
 * {@link #PERMISSION_DENIED} 회원 권한 {@code commerce.purchase} 없음 — 포인트 상점과 같은 코드), 문구 키 {@code groupbuy.blocked.<코드>}.
 * 본인인증·19세는 {@code IDENTITY_*}, 공구 참여 제한은 {@code MEMBER_RESTRICTED} 다.
 */
public class GroupBuyBlockedException extends CommonException {

    public static final String ACCOUNT_RESTRICTED = "ACCOUNT_RESTRICTED";
    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED";

    public GroupBuyBlockedException(String reason) {
        super(HttpStatus.FORBIDDEN, reason, new MessageRef("groupbuy.blocked." + reason));
    }
}
