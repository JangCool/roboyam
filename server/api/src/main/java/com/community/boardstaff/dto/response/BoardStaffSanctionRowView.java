package com.community.boardstaff.dto.response;

import java.time.Instant;

/**
 * 제재 내역 한 줄(user-web {@code BoardStaffSanctionRow}).
 *
 * @param issuer  매니저에게만(스텝이 보면 null)
 * @param lift    해제했으면 사유·시각
 * @param source  계기 글·댓글(없으면 null)
 * @param canLift 지금 보는 사람이 [해제]할 수 있다(매니저 + {@code board.staff-action.restore} · 진행 중인 쓰기 제한)
 */
public record BoardStaffSanctionRowView(String id, long no, BoardSanctionTargetView target, String kind, String reasonCode, String reasonText, String status,
                                        Instant startsAt, Instant endsAt, Instant createdAt, BoardSanctionIssuerView issuer, BoardSanctionLiftInfoView lift,
                                        BoardSanctionSourceView source, boolean canLift) {
}
