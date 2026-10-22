package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 내 글·댓글은 추천할 수 없음(400) */
public class CannotLikeOwnException extends CommonException {

    /** @param comment 댓글이면 true(문구만 다르다) */
    public CannotLikeOwnException(boolean comment) {
        super(HttpStatus.BAD_REQUEST, "CANNOT_LIKE_OWN", new MessageRef(comment ? "error.CANNOT_LIKE_OWN.comment" : "error.CANNOT_LIKE_OWN"));
    }
}
