package com.community.emoticon.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 403 — 로보티콘을 만들어 올릴 자격이 없다(계약 가정 9 — 로그인 · 이메일 인증 · 정상 회원). 코드는 까닭 그대로({@link #ACCOUNT_RESTRICTED} 정지·영구 정지 ·
 * {@link #EMAIL_NOT_VERIFIED} 이메일 인증 전 — 포인트 상점·글쓰기와 같은 코드). 문구 키는 {@code emoticon.blocked.<코드>}.
 */
public class EmoticonCreatorBlockedException extends CommonException {

    public static final String ACCOUNT_RESTRICTED = "ACCOUNT_RESTRICTED";
    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";

    public EmoticonCreatorBlockedException(String reason) {
        super(HttpStatus.FORBIDDEN, reason, new MessageRef("emoticon.blocked." + reason));
    }
}
