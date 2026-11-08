package com.community.post.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 400 — 비밀번호로 여는 글이 아니다(비회원 비밀글이 아님 — 회원 글이거나 비밀글이 아니다, V168 {@code POST /api/posts/{id}/secret-unlock}) */
public class SecretUnlockNotGuestException extends CommonException {

    public SecretUnlockNotGuestException() {
        super(HttpStatus.BAD_REQUEST, "SECRET_UNLOCK_NOT_GUEST", new MessageRef("error.SECRET_UNLOCK_NOT_GUEST"));
    }
}
