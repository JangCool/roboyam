package com.community.boardstaff.dto.response;

import java.time.Instant;

/** 처리 응답(user-web {@code BoardStaffActionDone}) — 그 종류의 남은 한도(글 처리면 글 한도, 댓글 삭제면 댓글 한도)와 다시 채워지는 시각 */
public record BoardStaffActionDoneView(BoardStaffQuotaView quota, Instant resetsAt) {
}
