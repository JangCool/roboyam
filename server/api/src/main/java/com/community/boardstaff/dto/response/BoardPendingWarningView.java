package com.community.boardstaff.dto.response;

import com.community.sanction.repository.master.BoardSanctionStore.PendingWarning;
import java.time.Instant;

/** 게시판 응답의 {@code viewer.pendingWarnings} 한 줄(user-web {@code BoardPendingWarning}) — 보는 내가 이 게시판에서 받고 아직 확인하지 않은 경고. 처리자는 없다 */
public record BoardPendingWarningView(String boardSanctionId, String reasonCode, Instant issuedAt) {

    public static BoardPendingWarningView of(PendingWarning warning) {
        return new BoardPendingWarningView(Long.toString(warning.id()), warning.reasonCode(), warning.issuedAt());
    }
}
