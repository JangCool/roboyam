package com.community.nicknameimage.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET·PUT /api/admin/nickname-image-settings} — 닉네임 그림 신청 조건.
 *
 * @param gradeKey     고른 최소 등급 — CUSTOM 에서 등급 조건이 없으면 null
 * @param gradeMissing 고른 등급이 등급 목록에 없다(지워졌다) — 회원은 등급 조건을 채울 수 없다
 * @param grades       등급 고르개(서열 순)
 * @param updatedAt    마지막 저장 시각 — 처음 상태면 null
 * @param updatedBy    마지막으로 저장한 운영자 — 처음 상태면 null, 운영자가 지워졌으면 이름이 null
 */
public record NicknameImageSettingsView(String mode, String gradeKey, int minDays, int minActivity, int minPoints, boolean requireEmailVerified,
                                        boolean gradeMissing, List<NicknameImageGradeOptionView> grades, Instant updatedAt, NicknameImageReviewerView updatedBy) {
}
