package com.community.member.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 조회 기간의 시작이 끝보다 늦다(400). {@code fields} 에 어느 기간인지(예: {@code joinedFrom}) 싣는다. */
public class MemberPeriodReversedException extends CommonException {

    public MemberPeriodReversedException(String fromField) {
        super(HttpStatus.BAD_REQUEST, "MEMBER_PERIOD_REVERSED", new MessageRef("error.MEMBER_PERIOD_REVERSED"),
                Map.of(fromField, new MessageRef("error.MEMBER_PERIOD_REVERSED")), null);
    }
}
