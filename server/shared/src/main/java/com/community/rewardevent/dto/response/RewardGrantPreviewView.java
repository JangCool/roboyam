package com.community.rewardevent.dto.response;

import java.util.List;

/**
 * 지급 미리 보기(admin-web {@code RewardGrantPreview}) — {@code largeRequired} 는 서버 판정(1인 포인트 합이 고액 문턱을 넘거나, 일괄 방식에 포인트 보상이 있다).
 */
public record RewardGrantPreviewView(long targetCount, long maxPointsPerPerson, long totalPoints, long itemCount, long manualCount, List<Tier> tiers, int largeThreshold,
                                     boolean largeRequired) {

    public record Tier(String tierId, String tierName, long count, long points, long items, long manual) {
    }
}
