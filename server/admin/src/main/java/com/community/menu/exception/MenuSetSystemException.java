package com.community.menu.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 기본 메뉴 그룹은 지울 수 없다(409) */
public class MenuSetSystemException extends CommonException {

    public MenuSetSystemException() {
        super(HttpStatus.CONFLICT, "MENU_SET_SYSTEM", new MessageRef("error.MENU_SET_SYSTEM"), Map.of(), null);
    }
}
