package com.community.point.dto.response;

import com.community.point.dto.PointRules;
import java.time.Instant;

/**
 * {@code GET·PUT /api/admin/settings/points} — admin-web {@code PointSettingsResponse}(14칸이 최상위 + 마지막 변경).
 *
 * @param updatedAt 마지막 저장 시각 — 처음 상태면 null
 * @param updatedBy 마지막으로 저장한 운영자 이름 — 처음 상태이거나 운영자가 지워졌으면 null
 */
public record PointSettingsResponse(int attendancePoints, int postPoints, int postDailyCap, int commentPoints, int commentDailyCap, int popularRewardPoints,
                                    int popularRewardTopN, int likeBonusThreshold, int likeBonusPoints, int likeBonusDailyLimit, int dealEndRequestPoints,
                                    int dealEndRequestDailyLimit, int signupPoints, int nicknameTicketPrice, Instant updatedAt, String updatedBy) {

    public static PointSettingsResponse of(PointRules r, Instant updatedAt, String updatedBy) {
        return new PointSettingsResponse(r.attendancePoints(), r.postPoints(), r.postDailyCap(), r.commentPoints(), r.commentDailyCap(), r.popularRewardPoints(),
                r.popularRewardTopN(), r.likeBonusThreshold(), r.likeBonusPoints(), r.likeBonusDailyLimit(), r.dealEndRequestPoints(), r.dealEndRequestDailyLimit(),
                r.signupPoints(), r.nicknameTicketPrice(), updatedAt, updatedBy);
    }
}
