package com.community.nicknameimage.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 닉네임 그림이 없음(404 {@code NICKNAME_IMAGE_NOT_FOUND}) — 회원: 해제할 적용 중인 그림이 없다. 운영자: 없는·형식이 틀린 신청 ID.
 */
public class NicknameImageNotFoundException extends CommonException {

    public NicknameImageNotFoundException() {
        this("error.NICKNAME_IMAGE_NOT_FOUND");
    }

    private NicknameImageNotFoundException(String messageKey) {
        super(HttpStatus.NOT_FOUND, "NICKNAME_IMAGE_NOT_FOUND", new MessageRef(messageKey));
    }

    /** 관리자 심사 — 없는 심사 건(운영자 용어 「닉네임 이미지 심사」의 문구) */
    public static NicknameImageNotFoundException forReview() {
        return new NicknameImageNotFoundException("nicknameImage.admin.error.NOT_FOUND");
    }
}
