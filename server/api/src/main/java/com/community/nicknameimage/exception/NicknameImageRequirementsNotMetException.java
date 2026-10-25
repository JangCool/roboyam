package com.community.nicknameimage.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 관리자가 정한 닉네임 그림 신청 조건(등급·가입 일수·글+댓글 수·포인트)을 채우지 못함(403 {@code NICKNAME_IMAGE_REQUIREMENTS_NOT_MET}) — 무엇이 모자란지는 {@code GET} 의 {@code requirements} */
public class NicknameImageRequirementsNotMetException extends CommonException {

    public NicknameImageRequirementsNotMetException() {
        super(HttpStatus.FORBIDDEN, "NICKNAME_IMAGE_REQUIREMENTS_NOT_MET", new MessageRef("error.NICKNAME_IMAGE_REQUIREMENTS_NOT_MET"));
    }
}
