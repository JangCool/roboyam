package com.community.grade.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 첫 등급(기본 등급)을 지우려 했다(409). */
public class GradeFirstProtectedException extends CommonException {

    public GradeFirstProtectedException() {
        super(HttpStatus.CONFLICT, "GRADE_FIRST_PROTECTED", new MessageRef("error.GRADE_FIRST_PROTECTED"), Map.of(), null);
    }
}
