package com.community.aimember.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 수정 후 게시한 글에 금지어가 있다(400) — 사이트 글쓰기와 같은 코드·문구 {@code BLOCKED_WORD}(걸린 단어는 운영자가 정한 값) */
public class AiDraftBlockedWordException extends CommonException {

    public AiDraftBlockedWordException(String word) {
        super(HttpStatus.BAD_REQUEST, "BLOCKED_WORD", new MessageRef("error.BLOCKED_WORD", word));
    }
}
