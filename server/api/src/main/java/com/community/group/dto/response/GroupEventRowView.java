package com.community.group.dto.response;

import java.time.Instant;

/**
 * 일정 목록의 한 줄(community {@code EventRow}, 상세의 머리와 같다).
 *
 * @param startsAt   시작(ISO-8601 UTC)
 * @param endsAt     끝(정하지 않았으면 null)
 * @param mode       ONLINE · OFFLINE
 * @param place      장소(온라인이면 null)
 * @param state      UPCOMING(시작 전 — 응답 가능) · ONGOING(진행 중) · ENDED(끝남 — 끝이 없으면 시작 2시간 뒤) · CANCELED(취소) — 서버가 지금 시각으로 정한다
 * @param goingCount 지금 멤버의 참석 응답 수
 * @param myResponse 내 응답 GOING · NOT_GOING · MAYBE, 없으면 null
 */
public record GroupEventRowView(String id, String title, Instant startsAt, Instant endsAt, String mode, String place, String state, int goingCount,
                                String myResponse) {
}
