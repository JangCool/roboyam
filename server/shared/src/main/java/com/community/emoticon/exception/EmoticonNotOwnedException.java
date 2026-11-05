package com.community.emoticon.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 409 — 글·댓글 본문에 작성자가 가지지 않은 팩의 로보티콘 토큰이 있다(계약 「목업 5차」 — {@code EmoticonGuard}). 최상위 {@code emoticonId} = 본문 순서로 처음 걸린 로보티콘 ID(문자열).
 */
public class EmoticonNotOwnedException extends CommonException {

    public EmoticonNotOwnedException(long emoticonId) {
        super(HttpStatus.CONFLICT, "EMOTICON_NOT_OWNED", new MessageRef("error.EMOTICON_NOT_OWNED"));
        extra("emoticonId", Long.toString(emoticonId));
    }
}
