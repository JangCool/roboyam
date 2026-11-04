package com.community.boardstaff.dto.response;

import java.time.Instant;

/** {@code POST /api/board-staff/sanctions} 201(user-web {@code BoardSanctionIssued}) — 남은 한도를 함께 준다. {@code endsAt} 쓰기 제한만 */
public record BoardSanctionIssuedView(String id, long no, String kind, Instant startsAt, Instant endsAt, BoardStaffQuotaView quota, Instant resetsAt) {
}
