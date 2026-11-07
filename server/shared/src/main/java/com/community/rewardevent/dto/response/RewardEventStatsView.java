package com.community.rewardevent.dto.response;

/**
 * 숫자(admin-web {@code RewardEventStats}) — 사람 단위(받는 사람 상태) + 줄 단위(건너뛴 보상 줄). 지급 포인트는 회수분을 뺀다. 아이템 = 상점 아이템 수량 + 팩.
 */
public record RewardEventStatsView(long targets, long pending, long granted, long partial, long failed, long skipped, long revoked, long skippedLines, long grantedPoints,
                                   long grantedItems, long manualPending, long manualDelivered) {

    public static final RewardEventStatsView EMPTY = new RewardEventStatsView(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
}
