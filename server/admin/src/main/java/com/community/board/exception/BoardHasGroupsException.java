package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 소모임이 쓰는 소모임 게시판은 지울 수 없다(409 — 해산·반려된 소모임도 기록으로 이 게시판을 가리킨다) — 대신 끈다 */
public class BoardHasGroupsException extends CommonException {

    public BoardHasGroupsException() {
        super(HttpStatus.CONFLICT, "BOARD_HAS_GROUPS", new MessageRef("error.BOARD_HAS_GROUPS"), Map.of(), null);
    }
}
