package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 등급이 개설 최소 등급보다 낮다(403) — 문구에 정한 등급 이름, 오류 본문 최상위 {@code minGrade}(등급 키) */
public class GroupGradeTooLowException extends CommonException {

    public GroupGradeTooLowException(String minGradeKey, String minGradeLabel) {
        super(HttpStatus.FORBIDDEN, "GROUP_GRADE_TOO_LOW", new MessageRef("error.GROUP_GRADE_TOO_LOW", minGradeLabel == null ? minGradeKey : minGradeLabel));
        extra("minGrade", minGradeKey);
    }
}
