package com.community.comment.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이 게시판은 댓글 비추천을 받지 않는다(409) — 게시판 정책 commentDislikeEnabled 가 꺼졌다 */
public class CommentDislikeDisabledException extends CommonException {

    public CommentDislikeDisabledException() {
        super(HttpStatus.CONFLICT, "COMMENT_DISLIKE_DISABLED", new MessageRef("error.COMMENT_DISLIKE_DISABLED"));
    }
}
