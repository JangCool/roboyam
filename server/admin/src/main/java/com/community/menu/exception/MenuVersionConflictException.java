package com.community.menu.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 그사이 다른 사람이(또는 게시판 자동 추가·삭제가) 메뉴를 바꿨다(409) — 공개 메뉴 트리 저장·관리자 메뉴 저장이 같이 쓴다 */
public class MenuVersionConflictException extends CommonException {

    public MenuVersionConflictException() {
        super(HttpStatus.CONFLICT, "MENU_VERSION_CONFLICT", new MessageRef("error.MENU_VERSION_CONFLICT"), Map.of(), null);
    }
}
