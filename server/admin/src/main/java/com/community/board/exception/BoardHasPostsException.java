package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 글이 있는 게시판은 지울 수 없다(409) — 대신 끈다 */
public class BoardHasPostsException extends CommonException {

    public BoardHasPostsException() {
        super(HttpStatus.CONFLICT, "BOARD_HAS_POSTS", new MessageRef("error.BOARD_HAS_POSTS"), Map.of(), null);
    }
}
