package com.community.grade.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 등급 수가 최소·최대 단계 밖이다(409). */
public class GradeLimitException extends CommonException {

    public GradeLimitException(long min, long max) {
        super(HttpStatus.CONFLICT, "GRADE_LIMIT", new MessageRef("error.GRADE_LIMIT", String.valueOf(min), String.valueOf(max)), Map.of(), null);
    }
}
