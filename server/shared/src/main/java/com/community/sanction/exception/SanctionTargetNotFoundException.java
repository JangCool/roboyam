package com.community.sanction.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 제재할 회원이 없거나 탈퇴했다(탈퇴 진행 중 포함) · 신고 사건의 작성자가 비회원·탈퇴 회원 */
public class SanctionTargetNotFoundException extends CommonException {

    public static final String CODE = "SANCTION_TARGET_NOT_FOUND";

    public SanctionTargetNotFoundException() {
        super(HttpStatus.NOT_FOUND, CODE, new MessageRef("error.SANCTION_TARGET_NOT_FOUND"));
    }
}
