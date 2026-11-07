package com.community.notice.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 공지 게시판이 꺼져 있어 쓸 수 없다(409 NOTICE_BOARD_INACTIVE) — 목록·보기는 된다 */
public class NoticeBoardInactiveException extends CommonException {

    public NoticeBoardInactiveException() {
        super(HttpStatus.CONFLICT, "NOTICE_BOARD_INACTIVE", new MessageRef("error.NOTICE_BOARD_INACTIVE"));
    }
}
