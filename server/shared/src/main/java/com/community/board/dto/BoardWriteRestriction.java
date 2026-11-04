package com.community.board.dto;

import java.time.Instant;

/**
 * 이 회원이 한 게시판에서 받은 진행 중인 쓰기 제한(게시판 제재 — docs/features/board-sanction-contract.md 결정 5). 처리자는 싣지 않는다(결정 7).
 * 그 게시판의 새 글·댓글·답글만 막는다(user-web {@code BoardWriteRestriction}).
 *
 * @param until      끝나는 시각
 * @param reasonCode 사유 코드(SPAM · ABUSE · INVESTMENT · OFF_TOPIC · OTHER)
 */
public record BoardWriteRestriction(long boardSanctionId, Instant until, String reasonCode) {
}
