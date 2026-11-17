package com.community.title.dto.request;

/**
 * 작가 칭호 설정 저장({@code PUT /api/admin/titles/settings}) — 이달의 작가 한 달 최대 인원 1~20 · 명예 작가가 되는 이달의 작가 횟수 1~24 · 거절 뒤 다시 신청까지 일수 1~365(없으면 그대로).
 */
public record AdminTitleSettingsRequest(Integer monthlyMax, Integer honoraryCount, Integer reapplyDays) {
}
