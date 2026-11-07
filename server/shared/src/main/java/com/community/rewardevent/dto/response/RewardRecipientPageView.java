package com.community.rewardevent.dto.response;

import java.util.List;

/** 받는 사람 한 쪽(admin-web {@code RewardRecipientPage}) — 선정 시각 내림차순 */
public record RewardRecipientPageView(List<RewardRecipientView> items, long total) {
}
