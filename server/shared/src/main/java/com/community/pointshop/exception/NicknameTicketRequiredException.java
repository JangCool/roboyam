package com.community.pointshop.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 409 — 가입 뒤 1번의 무료 닉네임 변경을 다 썼고 변경권을 쓰지 않았거나({@code useTicket} 없음) 쓸 변경권이 없다(member-mypage-contract 「후속 작업: 닉네임 변경권」).
 * 오류 본문 최상위 {@code ticketCount}(안 쓴 변경권 수)·{@code ticketPrice}(한 장 값 P — 변경권 품목이 없으면 null).
 */
public class NicknameTicketRequiredException extends CommonException {

    public NicknameTicketRequiredException(int ticketCount, Integer ticketPrice) {
        super(HttpStatus.CONFLICT, "NICKNAME_TICKET_REQUIRED", new MessageRef("error.NICKNAME_TICKET_REQUIRED"));
        extra("ticketCount", ticketCount);
        extra("ticketPrice", ticketPrice);
    }
}
