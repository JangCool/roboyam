package com.community.emoticon.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 409 {@code EMOTICON_REFUND_NOT_ALLOWED} — 셀프 환불 조건 밖(계약 가정 3 · commerce.md 7-3). 최상위 {@code blockedBy} = {@link #USED}(그 팩의 로보티콘을 한 번이라도 썼다) ·
 * {@link #EXPIRED}(환불 기간이 지났다). 둘 다면 {@code USED}(목업과 같다). 문구 키 {@code error.EMOTICON_REFUND_NOT_ALLOWED.<까닭>}.
 */
public class EmoticonRefundNotAllowedException extends CommonException {

    public static final String USED = "USED";
    public static final String EXPIRED = "EXPIRED";

    public EmoticonRefundNotAllowedException(String blockedBy) {
        super(HttpStatus.CONFLICT, "EMOTICON_REFUND_NOT_ALLOWED", new MessageRef("error.EMOTICON_REFUND_NOT_ALLOWED." + blockedBy));
        extra("blockedBy", blockedBy);
    }
}
