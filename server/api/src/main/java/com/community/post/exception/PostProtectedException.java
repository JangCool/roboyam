package com.community.post.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 댓글 많은 글 보호 중이라 고치거나 지울 수 없음(409) — 보호 기준(댓글 수)을 문구에 싣는다 */
public class PostProtectedException extends CommonException {

    /**
     * @param delete    지우기면 true, 고치기면 false(문구만 다르다)
     * @param threshold 보호 기준 댓글 수
     */
    public PostProtectedException(boolean delete, int threshold) {
        super(HttpStatus.CONFLICT, "POST_PROTECTED", new MessageRef(delete ? "error.POST_PROTECTED.delete" : "error.POST_PROTECTED", threshold));
    }
}
