package com.community.group.dto.request;

/**
 * 일정 만들기·수정(community api-groups.md 9절) — 수정도 모든 칸을 보낸다. 시각·진행 방식이 틀려도 JSON 오류가 아니라 칸별 입력 오류로 알리려고 모두 문자열로 받는다.
 *
 * @param title       제목 1~60자
 * @param startsAt    시작 — ISO-8601 오프셋 포함(예 {@code 2026-11-02T11:00:00Z})
 * @param endsAt      끝(선택) — 시작 뒤 운영 한도 일수 안
 * @param mode        ONLINE · OFFLINE
 * @param place       장소 100자 이하(오프라인 필수, 온라인이면 무시)
 * @param description 설명 1000자 이하(선택)
 * @param offlineAck  개인 책임 안내 확인 — 오프라인 일정을 새로 만들 때(또는 온라인을 오프라인으로 바꿀 때) true 필수
 */
public record GroupEventRequest(String title, String startsAt, String endsAt, String mode, String place, String description, Boolean offlineAck) {
}
