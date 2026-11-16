package com.community.point.dto.response;

import com.community.point.dto.PointLotRules;
import com.community.point.dto.PointRules;
import com.community.point.dto.PointTierRules;
import com.community.point.dto.PointTierValues;
import com.community.point.entity.PointBoardTier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Instant;

/**
 * {@code GET·PUT /api/admin/settings/points} — admin-web {@code PointSettingsResponse}(정책 칸이 최상위 + 마지막 변경 — 닉네임 변경권 가격은 커머스 1-2 에서 포인트 상점 품목으로 옮겼다).
 * 커머스 1-3: 적립 조건 8칸(켬·끔 2칸 포함)을 더했고 {@code adjustLargeThreshold} 를 저장한다.
 *
 * @param adjustLargeThreshold |운영 조정| 이 이 값을 넘으면 {@code point.adjust.large} 가 필요하다(0 = 모든 조정, 0~100,000)
 * @param boardTiers 게시판 등급별 금액·조건 {@code NORMAL|INFO|CORE → {postPoints, likeBonusPoints, featuredPoints, minLength, minImages, holdHours}}(point-board-tier-contract) —
 *                   일반 등급의 {@code postPoints}·{@code minLength} 는 전역 칸 {@code postPoints}·{@code postMinLength} 값이다
 * @param infoSourceOnce 정보 등급 같은 출처 링크 한 번만 적립
 * @param excludeAiAndOps AI 회원·운영팀 계정 적립 안 함
 * @param updatedAt 마지막 저장 시각 — 처음 상태면 null
 * @param updatedBy 마지막으로 저장한 운영자 이름 — 처음 상태이거나 운영자가 지워졌으면 null
 */
public record PointSettingsResponse(int attendancePoints, int postPoints, int postDailyCap, int commentPoints, int commentDailyCap, int popularRewardPoints,
                                    int popularRewardTopN, int likeBonusThreshold, int likeBonusPoints, int likeBonusDailyLimit, int dealEndRequestPoints,
                                    int dealEndRequestDailyLimit, int signupPoints, int postMinLength, int commentMinLength, boolean commentOwnPostExcluded,
                                    boolean commentOncePerPost, int writeIntervalSeconds, int dailyTotalCap, int attendanceStreakDays, int attendanceStreakBonus,
                                    int adjustLargeThreshold, Map<String, PointTierValues> boardTiers, boolean infoSourceOnce, boolean excludeAiAndOps,
                                    Instant updatedAt, String updatedBy) {

    public static PointSettingsResponse of(PointRules r, PointLotRules lots, PointTierRules tiers, Instant updatedAt, String updatedBy) {
        Map<String, PointTierValues> boardTiers = new LinkedHashMap<>();
        for (PointBoardTier tier : PointBoardTier.values()) {
            boardTiers.put(tier.name(), tiers.values(tier, r));
        }
        return new PointSettingsResponse(r.attendancePoints(), r.postPoints(), r.postDailyCap(), r.commentPoints(), r.commentDailyCap(), r.popularRewardPoints(),
                r.popularRewardTopN(), r.likeBonusThreshold(), r.likeBonusPoints(), r.likeBonusDailyLimit(), r.dealEndRequestPoints(), r.dealEndRequestDailyLimit(),
                r.signupPoints(), r.postMinLength(), r.commentMinLength(), r.commentOwnPostExcluded(), r.commentOncePerPost(), r.writeIntervalSeconds(),
                r.dailyTotalCap(), r.attendanceStreakDays(), r.attendanceStreakBonus(), lots.adjustLargeThreshold(), boardTiers, tiers.infoSourceOnce(), tiers.excludeAiAndOps(),
                updatedAt, updatedBy);
    }
}
