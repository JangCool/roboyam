package com.community.notice.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 공지 게시판(slug {@code notice}, 소모임 게시판 아님)이 없다(404 NOTICE_BOARD_MISSING) */
public class NoticeBoardMissingException extends CommonException {

    public NoticeBoardMissingException() {
        super(HttpStatus.NOT_FOUND, "NOTICE_BOARD_MISSING", new MessageRef("error.NOTICE_BOARD_MISSING"));
    }
}
