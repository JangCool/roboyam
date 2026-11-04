package com.community.boardstaff.dto.response;

import com.community.board.dto.BoardWriteRestriction;
import java.time.Instant;

/** 게시판 응답의 {@code viewer.writeRestriction}(user-web {@code BoardWriteRestriction}) — 보는 내가 이 게시판에서 쓰기 제한 중이면. 처리자는 없다 */
public record BoardWriteRestrictionView(String boardSanctionId, Instant until, String reasonCode) {

    public static BoardWriteRestrictionView of(BoardWriteRestriction restriction) {
        return restriction == null ? null
                : new BoardWriteRestrictionView(Long.toString(restriction.boardSanctionId()), restriction.until(), restriction.reasonCode());
    }
}
