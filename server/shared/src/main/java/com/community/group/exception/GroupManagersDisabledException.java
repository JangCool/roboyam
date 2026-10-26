package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 매니저 규칙의 「매니저 기능」이 꺼져 있어 새 임명·맡은 일 바꾸기를 할 수 없다(403 — 3단계). 해임·사퇴·인원 설정·이미 있는 매니저의 판정은 꺼져 있어도 동작한다 —
 * 코드 {@code GROUP_MANAGERS_DISABLED}
 */
public class GroupManagersDisabledException extends CommonException {

    public GroupManagersDisabledException() {
        super(HttpStatus.FORBIDDEN, "GROUP_MANAGERS_DISABLED", new MessageRef("error.GROUP_MANAGERS_DISABLED"));
    }
}
