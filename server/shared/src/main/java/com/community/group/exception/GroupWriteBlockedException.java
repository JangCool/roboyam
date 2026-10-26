package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 소모임 쓰기 자격이 없다(403) — 코드는 까닭 그대로 {@code EMAIL_NOT_VERIFIED}(이메일 인증 전) · {@code ACCOUNT_RESTRICTED}(정지·영구 정지) · {@code BUSINESS_ACCOUNT_RESTRICTED}(사업자 회원 — 로보얌).
 * 가입·신청·탈퇴·개설·모임장 관리 쓰기 모두 같은 판정이다(모임장 포함). 문구 키는 {@code group.error.<까닭>}(글쓰기 문구와 따로).
 */
public class GroupWriteBlockedException extends CommonException {

    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";
    public static final String ACCOUNT_RESTRICTED = "ACCOUNT_RESTRICTED";
    /** 사업자 회원(게시판 글쓰기와 같은 코드 — 로보얌) */
    public static final String BUSINESS_ACCOUNT_RESTRICTED = "BUSINESS_ACCOUNT_RESTRICTED";

    public GroupWriteBlockedException(String reason) {
        super(HttpStatus.FORBIDDEN, reason, new MessageRef("group.error." + reason));
    }
}
