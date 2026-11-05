package com.community.emoticon.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;

/**
 * 400 {@code VALIDATION} — 올린 그림이 규격에 맞지 않는다. 화면 목업과 같은 모양: {@code fields.<칸>} = <b>이유 코드</b>({@code TYPE}·{@code COVER_TYPE}·{@code DIMENSION}·
 * {@code TOO_LARGE}·{@code TOO_MANY_FRAMES}·{@code NOT_LOOP} — user-web {@code UtilEmoticonSpec}), {@code message} = 이유마다 다른 한 줄(한도·지금 값). 칸은 작가 올리기·운영 팩
 * 로보티콘 올리기가 {@code file}, 운영 팩 대표 이미지가 {@code cover}.
 */
public class EmoticonImageInvalidException extends CommonException {

    private final String reason;

    public EmoticonImageInvalidException(String field, String reason, MessageRef message) {
        super(ErrorCode.VALIDATION.getStatus(), ErrorCode.VALIDATION.name(), message, Map.of(field, new MessageRef("emoticon.image.reason", reason)), null);
        this.reason = reason;
    }

    /** 이유 코드 */
    public String reason() {
        return reason;
    }
}
