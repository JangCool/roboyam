package com.community.home.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 화면이 배치를 받은 뒤 다른 저장이 있었다(409) — 홈 배치·가젯 페이지 배치·게시판 레이아웃 저장이 같이 쓴다(관리자 QA 2026-11-17 F-1).
 * 저장 본문이 「가젯 전체」라 낡은 화면이 그대로 저장하면 그사이 남이 넣은 가젯이 지워지므로 막는다. 메뉴의 {@code MENU_VERSION_CONFLICT} 와 같은 방식.
 */
public class LayoutVersionConflictException extends CommonException {

    public LayoutVersionConflictException() {
        super(HttpStatus.CONFLICT, "LAYOUT_VERSION_CONFLICT", new MessageRef("error.LAYOUT_VERSION_CONFLICT"), Map.of(), null);
    }
}
