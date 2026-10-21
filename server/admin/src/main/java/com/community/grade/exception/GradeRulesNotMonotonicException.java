package com.community.grade.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 위 등급일수록 승급 조건이 같거나 커야 하는데 거꾸로다(409) — 삭제·이동·추가 뒤 남은 목록 기준으로 판정한다. */
public class GradeRulesNotMonotonicException extends CommonException {

    public GradeRulesNotMonotonicException(int position) {
        super(HttpStatus.CONFLICT, "GRADE_RULES_NOT_MONOTONIC", new MessageRef("error.GRADE_RULES_NOT_MONOTONIC", String.valueOf(position)), Map.of(), null);
    }
}
