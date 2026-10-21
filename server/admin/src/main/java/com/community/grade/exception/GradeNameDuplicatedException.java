package com.community.grade.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 등급 이름이 다른 등급과 겹친다(409). 문구에 몇 번째 등급인지만 싣는다(입력한 이름은 싣지 않는다). */
public class GradeNameDuplicatedException extends CommonException {

    public GradeNameDuplicatedException(int position) {
        super(HttpStatus.CONFLICT, "GRADE_NAME_DUPLICATED", new MessageRef("error.GRADE_NAME_DUPLICATED", String.valueOf(position)), Map.of(), null);
    }
}
