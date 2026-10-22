package com.community.board.dto.response;

import com.community.board.dto.BoardPrivacyValues;
import java.time.Instant;
import java.util.Map;

/**
 * {@code GET·PUT /api/admin/boards/defaults/privacy}(admin-web {@code BoardPrivacyResponse}).
 *
 * @param targets                 축별 대상 수(저장된 설정 기준) — ip · guest · deletedContent · reportRecord · staffAction
 * @param attachmentRetentionDays 일반 기본 설정의 삭제 첨부 보존 기간(같게 두라는 경고용)
 * @param lastRunAt               마지막 배치(또는 지금 실행) 시각 — 없으면 null
 */
public record BoardPrivacyResponse(BoardPrivacyValues settings, Instant updatedAt, String updatedBy, Map<String, Long> targets, int attachmentRetentionDays,
                                   Instant lastRunAt) {
}
