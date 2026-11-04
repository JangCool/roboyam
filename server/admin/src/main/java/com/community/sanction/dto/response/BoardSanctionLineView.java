package com.community.sanction.dto.response;

import java.time.Instant;

/**
 * 게시판 제재 한 줄(admin-web {@code BoardSanctionLine}) — 목록·상세·회원 이력·반복 검토·이의제기 상세가 같이 쓴다. 처리자(매니저·스텝) 신원은 관리자 응답에만 있다(결정 7).
 *
 * @param status         지금 상태 — 끝이 지난(크론 전) 쓰기 제한은 ENDED
 * @param voidedBy       무효로 한 운영자(이의 인용이면 답한 운영자)
 * @param voidSource     OPERATOR · REVIEW · APPEAL
 * @param endedAt        기간이 끝난 시각(크론 전이면 끝 시각)
 * @param appealDeadline 이의제기 마감(시작 + 30일) — 무효면 null
 */
public record BoardSanctionLineView(String id, long no, String kind, String status, BoardSanctionBoardRefView board, SanctionUserRefView user, String reasonCode,
                                    String reasonText, BoardSanctionIssuerView issuedBy, Instant startsAt, Instant endsAt, Instant createdAt,
                                    BoardSanctionTriggerView trigger, BoardSanctionLiftActorView liftedBy, Instant liftedAt, String liftReason, Instant voidedAt,
                                    SanctionActorRefView voidedBy, String voidSource, String voidReason, Instant endedAt, Instant acknowledgedAt,
                                    SanctionAppealRefView appeal, Instant appealDeadline) {
}
