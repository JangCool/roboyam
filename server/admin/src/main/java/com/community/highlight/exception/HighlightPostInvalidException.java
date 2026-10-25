package com.community.highlight.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 하이라이트로 지정할 수 없는 글이다(400) — {@code fields.postId} 에 까닭 문구: NOT_FOUND(없는 글) · NOT_PUBLIC(지웠거나 숨겼거나 공개 전) · ADULT(19금) · BOARD_OFF(게시판이 꺼짐).
 */
public class HighlightPostInvalidException extends CommonException {

    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String NOT_PUBLIC = "NOT_PUBLIC";
    public static final String ADULT = "ADULT";
    public static final String BOARD_OFF = "BOARD_OFF";

    public HighlightPostInvalidException(String reason) {
        super(HttpStatus.BAD_REQUEST, "HIGHLIGHT_POST_INVALID", new MessageRef("error.HIGHLIGHT_POST_INVALID"),
                Map.of("postId", new MessageRef("highlight.post." + reason)), null);
    }
}
