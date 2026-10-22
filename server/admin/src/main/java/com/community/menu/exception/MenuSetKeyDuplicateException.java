package com.community.menu.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 이미 있는 메뉴 그룹 코드(409, fields.key) */
public class MenuSetKeyDuplicateException extends CommonException {

    public MenuSetKeyDuplicateException() {
        super(HttpStatus.CONFLICT, "MENU_SET_KEY_DUPLICATE", new MessageRef("error.MENU_SET_KEY_DUPLICATE"),
                Map.of("key", new MessageRef("error.MENU_SET_KEY_DUPLICATE")), null);
    }
}
