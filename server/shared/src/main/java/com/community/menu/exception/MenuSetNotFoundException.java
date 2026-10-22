package com.community.menu.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 없는 메뉴 그룹(404) — 관리자 API 와 공개 {@code GET /api/menu} 가 같이 쓴다 */
public class MenuSetNotFoundException extends CommonException {

    public MenuSetNotFoundException() {
        super(HttpStatus.NOT_FOUND, "MENU_SET_NOT_FOUND", new MessageRef("error.MENU_SET_NOT_FOUND"), Map.of(), null);
    }
}
