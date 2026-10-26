package com.community.pointshop.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 403 — 포인트 상점·아바타를 쓸 자격이 없다. 코드는 까닭 그대로({@link #ACCOUNT_RESTRICTED} 정지·영구 정지 · {@link #EMAIL_NOT_VERIFIED} 이메일 인증 전 ·
 * {@link #PERMISSION_DENIED} 회원 권한 {@code pointshop.use} 없음 — 글쓰기·쪽지와 같은 코드). 문구 키는 {@code pointshop.blocked.<코드>}(글쓰기 문구와 다르다).
 * 포인트 동결(기능 제한)은 이 예외가 아니라 {@code 403 MEMBER_RESTRICTED}({@code MemberRestrictedException})다.
 */
public class PointShopBlockedException extends CommonException {

    public static final String ACCOUNT_RESTRICTED = "ACCOUNT_RESTRICTED";
    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED";

    public PointShopBlockedException(String reason) {
        super(HttpStatus.FORBIDDEN, reason, new MessageRef("pointshop.blocked." + reason));
    }
}
