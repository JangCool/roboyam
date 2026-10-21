package com.community.role.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 이미 있는 역할 아이디다(409). 아이디 칸({@code roleId})에 문구가 붙는다 */
public class RoleDuplicatedException extends CommonException {

    public RoleDuplicatedException() {
        super(HttpStatus.CONFLICT, "ROLE_DUPLICATED", new MessageRef("error.ROLE_DUPLICATED"),
                Map.of("roleId", new MessageRef("role.id.duplicated")), null);
    }
}
