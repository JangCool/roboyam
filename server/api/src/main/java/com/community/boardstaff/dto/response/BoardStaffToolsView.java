package com.community.boardstaff.dto.response;

import java.time.Instant;

/**
 * 게시판 응답의 {@code viewer.staffTools}(user-web {@code BoardStaffTools}) — 그 게시판에서 제재를 줄 수 있는 매니저·스텝 본인에게만(그 밖에는 null).
 *
 * @param resetsAt 한도가 다시 채워지는 시각(다음 한국 시간 0시)
 * @param manager  매니저(그 게시판 제재 전체를 보고 해제) · 거짓이면 스텝(내가 준 것만)
 */
public record BoardStaffToolsView(BoardStaffQuotaView sanction, Instant resetsAt, boolean manager) {
}
