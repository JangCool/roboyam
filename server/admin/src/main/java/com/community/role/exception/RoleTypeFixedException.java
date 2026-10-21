package com.community.role.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 제품이 정한 유형(회원 유형·게시판 단위)에는 역할을 새로 만들 수 없다(409). */
public class RoleTypeFixedException extends CommonException {

    public RoleTypeFixedException() {
        super(HttpStatus.CONFLICT, "ROLE_TYPE_FIXED", new MessageRef("error.ROLE_TYPE_FIXED"));
    }
}
