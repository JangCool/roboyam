package com.community.debate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 이 글의 토론을 열거나 바꾸거나 닫을 수 없다(403) — 그 게시판 매니저도, 글쓴이 규칙(회원 글 + 게시판 {@code debateEnabled} + 토론이 없거나 글쓴이가 연 토론)도 아니다.
 * docs/features/post-debate-contract.md
 */
public class DebateManageDeniedException extends CommonException {

    public DebateManageDeniedException() {
        super(HttpStatus.FORBIDDEN, "DEBATE_MANAGE_DENIED", new MessageRef("error.DEBATE_MANAGE_DENIED"));
    }
}
