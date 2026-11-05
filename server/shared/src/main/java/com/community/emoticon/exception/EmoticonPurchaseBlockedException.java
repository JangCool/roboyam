package com.community.emoticon.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 403 — 유료 팩을 살 자격이 없다(계약 가정 2 — 로그인 · 이메일 인증 · 정상 회원, 그다음 본인인증·만 19세는 본인인증 모듈의 {@code IDENTITY_REQUIRED}·{@code IDENTITY_UNDERAGE}).
 * 코드는 까닭 그대로({@link #ACCOUNT_RESTRICTED} 정지·영구 정지 · {@link #EMAIL_NOT_VERIFIED} 이메일 인증 전 — 공구·포인트 상점과 같은 코드). 문구 키 {@code emoticon.purchaseBlocked.<코드>}.
 */
public class EmoticonPurchaseBlockedException extends CommonException {

    public static final String ACCOUNT_RESTRICTED = "ACCOUNT_RESTRICTED";
    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";

    public EmoticonPurchaseBlockedException(String reason) {
        super(HttpStatus.FORBIDDEN, reason, new MessageRef("emoticon.purchaseBlocked." + reason));
    }
}
