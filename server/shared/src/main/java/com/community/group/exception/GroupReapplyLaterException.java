package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 가입 신청이 거절된 지 운영 한도(재신청 대기 일수)가 지나지 않았다(429) — {@code retryAfterSeconds} */
public class GroupReapplyLaterException extends CommonException {

    public GroupReapplyLaterException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "GROUP_REAPPLY_LATER", new MessageRef("error.GROUP_REAPPLY_LATER"), Map.of(), Math.max(1, retryAfterSeconds));
    }
}
