package com.community.role.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 쓰는 운영자나 회원이 있는 역할은 지울 수 없다(409). 문구에 운영자 수·회원 수를 싣는다 */
public class RoleInUseException extends CommonException {

    public RoleInUseException(long operatorCount, long memberCount) {
        super(HttpStatus.CONFLICT, "ROLE_IN_USE", new MessageRef("error.ROLE_IN_USE", String.valueOf(operatorCount), String.valueOf(memberCount)),
                Map.of(), null);
    }
}
