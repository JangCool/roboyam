package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 이미 쓰는 게시판 주소(409, fields.slug) */
public class BoardSlugDuplicateException extends CommonException {

    public BoardSlugDuplicateException() {
        super(HttpStatus.CONFLICT, "BOARD_SLUG_DUPLICATE", new MessageRef("error.BOARD_SLUG_DUPLICATE"), Map.of("slug", new MessageRef("error.BOARD_SLUG_DUPLICATE")), null);
    }
}
