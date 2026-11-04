package com.community.sanction.dto.response;

import java.time.Instant;

/**
 * 제재 한 줄(admin-web {@code SanctionLine}) — 목록·현황 이력·이의제기 상세가 같이 쓴다. 상태는 지금 시각의 상태(끝이 지난 정지는 ENDED).
 *
 * @param rank           단계 순위 1~6
 * @param warningNo      경고일 때 유효한 경고 중 몇 번째(1·2), 그 밖·무효 null
 * @param endsAt         기간 정지만
 * @param endedAt        기간 끝·영구 정지로 대체돼 끝난 시각
 * @param appealDeadline 이의제기 마감(시작 + 30일) — 무효면 null
 */
public record SanctionLineView(String id, long no, String kind, String status, int rank, Integer warningNo, boolean skipped, SanctionUserRefView user,
                               Instant startsAt, Instant endsAt, String reason, SanctionActorRefView issuedBy, Instant createdAt, SanctionReportRefView reportCase,
                               SanctionActorRefView liftedBy, Instant liftedAt, String liftReason, Instant voidedAt, Instant endedAt, SanctionAppealRefView appeal,
                               Instant appealDeadline) {
}
