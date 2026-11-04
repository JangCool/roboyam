package com.community.sanction.dto.response;

import java.time.Instant;

/**
 * 내 제재 한 줄(user-web {@code MySanction}).
 *
 * @param warningNo      경고일 때 유효한 경고 중 몇 번째(1·2), 그 밖 null
 * @param endsAt         기간 정지만
 * @param acknowledged   경고의 「확인했어요」 — 정지·영구 정지는 늘 true
 * @param appeal         이의제기(없으면 null)
 * @param appealDeadline 이의제기 마감(시작 + 30일) — 무효면 null
 */
public record MySanctionView(String id, long no, String kind, String status, String reason, Integer warningNo, Instant startsAt, Instant endsAt, Instant createdAt,
                             boolean acknowledged, MyAppealView appeal, Instant appealDeadline) {
}
