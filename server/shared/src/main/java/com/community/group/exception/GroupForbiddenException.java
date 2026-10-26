package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이 글에서 그 일을 할 수 있는 사람이 아니다(403 {@code FORBIDDEN}) — 공개 범위·공개 요청(api-groups.md 11-5·11-6)의 작성자·모임장·매니저가 아닌 경우 */
public class GroupForbiddenException extends CommonException {

    public GroupForbiddenException() {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", new MessageRef("error.FORBIDDEN.groupPost"));
    }
}
