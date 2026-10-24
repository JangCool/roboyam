package com.community.nicknameimage.dto.response;

import java.time.Instant;

/** {@code GET /api/me/nickname-image}(올리기 201 도 같은 모양) — user-web {@code MyNicknameImage} */
public record MyNicknameImageView(boolean eligible, String ineligibleReason, NicknameImageGradeView grade, NicknameImageGradeView requiredGrade,
                                  NicknameImageCurrentView current, NicknameImageRequestView request, Instant reapplyAt, NicknameImageLimitsView limits) {
}
