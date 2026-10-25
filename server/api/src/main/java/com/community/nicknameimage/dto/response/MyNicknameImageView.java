package com.community.nicknameimage.dto.response;

import java.time.Instant;

/**
 * {@code GET /api/me/nickname-image}(올리기 201 도 같은 모양) — user-web {@code MyNicknameImage}.
 *
 * @param eligible         정지가 아니고 모든 조건을 채웠다
 * @param ineligibleReason {@code REQUIREMENTS_NOT_MET} · {@code SUSPENDED} · null — 올리기가 낼 오류와 같은 차례(이메일 인증 조건 → 정지 → 나머지 조건)
 * @param grade            내 등급
 * @param requirements     신청 조건(켜진 것만)
 */
public record MyNicknameImageView(boolean eligible, String ineligibleReason, NicknameImageGradeView grade, NicknameImageRequirementsView requirements,
                                  NicknameImageCurrentView current, NicknameImageRequestView request, Instant reapplyAt, NicknameImageLimitsView limits) {
}
