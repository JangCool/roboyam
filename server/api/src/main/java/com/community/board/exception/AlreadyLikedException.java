package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 추천한 글·댓글(409) — 추천은 취소할 수 없다 */
public class AlreadyLikedException extends CommonException {

    /** @param comment 댓글이면 true(문구만 다르다) */
    public AlreadyLikedException(boolean comment) {
        super(HttpStatus.CONFLICT, "ALREADY_LIKED", new MessageRef(comment ? "error.ALREADY_LIKED.comment" : "error.ALREADY_LIKED"));
    }
}
