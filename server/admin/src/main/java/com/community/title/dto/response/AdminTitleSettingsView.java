package com.community.title.dto.response;

import java.time.Instant;

/**
 * 작가 칭호 설정({@code GET·PUT /api/admin/titles/settings}) — {@code honoraryGranted} 는 저장으로 명예 작가 기준을 내려 바로 소급해 준 회원 수(조회·그 밖 0).
 */
public record AdminTitleSettingsView(int monthlyMax, int honoraryCount, int reapplyDays, Instant updatedAt, int honoraryGranted) {
}
