package com.community.member.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 조회 조건의 포인트 최소가 최대보다 크다(400). */
public class MemberPointRangeReversedException extends CommonException {

    public MemberPointRangeReversedException() {
        super(HttpStatus.BAD_REQUEST, "MEMBER_POINT_RANGE_REVERSED", new MessageRef("error.MEMBER_POINT_RANGE_REVERSED"),
                Map.of("pointMin", new MessageRef("error.MEMBER_POINT_RANGE_REVERSED")), null);
    }
}
