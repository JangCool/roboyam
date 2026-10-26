package com.community.point.dto.response;

import com.community.point.dto.PointLotRules;
import com.community.point.dto.PointRules;
import java.time.Instant;

/**
 * {@code GET·PUT /api/admin/settings/points} — admin-web {@code PointSettingsResponse}(13칸이 최상위 + 마지막 변경 — 닉네임 변경권 가격은 커머스 1-2 에서 포인트 상점 품목으로 옮겼다).
 *
 * @param adjustLargeThreshold 읽기 전용 — |운영 조정| 이 이 값을 넘으면 {@code point.adjust.large} 가 필요하다(0 = 모든 조정). 조정 대화상자가 미리 안내하려고 읽는다.
 *                             저장(PUT)은 받지 않는다(정책 화면 칸은 1-3)
 * @param updatedAt 마지막 저장 시각 — 처음 상태면 null
 * @param updatedBy 마지막으로 저장한 운영자 이름 — 처음 상태이거나 운영자가 지워졌으면 null
 */
public record PointSettingsResponse(int attendancePoints, int postPoints, int postDailyCap, int commentPoints, int commentDailyCap, int popularRewardPoints,
                                    int popularRewardTopN, int likeBonusThreshold, int likeBonusPoints, int likeBonusDailyLimit, int dealEndRequestPoints,
                                    int dealEndRequestDailyLimit, int signupPoints, int adjustLargeThreshold, Instant updatedAt,
                                    String updatedBy) {

    public static PointSettingsResponse of(PointRules r, PointLotRules lots, Instant updatedAt, String updatedBy) {
        return new PointSettingsResponse(r.attendancePoints(), r.postPoints(), r.postDailyCap(), r.commentPoints(), r.commentDailyCap(), r.popularRewardPoints(),
                r.popularRewardTopN(), r.likeBonusThreshold(), r.likeBonusPoints(), r.likeBonusDailyLimit(), r.dealEndRequestPoints(), r.dealEndRequestDailyLimit(),
                r.signupPoints(), lots.adjustLargeThreshold(), updatedAt, updatedBy);
    }
}
