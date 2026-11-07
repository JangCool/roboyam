package com.community.pointshop.exception;

import com.community.pointshop.entity.UploadTicketUse;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 409 {@code NICKNAME_IMAGE_TICKET_REQUIRED} · {@code AVATAR_UPLOAD_TICKET_REQUIRED} — 신청에 예약할 변경권이 없다(commerce-motion-avatar-contract 규칙 0). 파일을 처리하기 전에 낸다.
 * 오류 본문 최상위 {@code ticketCount}(쓸 수 있는 장 수 — 예약된 장은 빼고)·{@code ticketPrice}(한 장 값 P — 품목이 없으면 null).
 */
public class UploadTicketRequiredException extends CommonException {

    public UploadTicketRequiredException(UploadTicketUse use, int ticketCount, Integer ticketPrice) {
        super(HttpStatus.CONFLICT, use.requiredCode(), new MessageRef("error." + use.requiredCode()));
        extra("ticketCount", ticketCount);
        extra("ticketPrice", ticketPrice);
    }
}
