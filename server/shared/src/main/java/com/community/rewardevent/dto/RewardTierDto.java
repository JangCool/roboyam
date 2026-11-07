package com.community.rewardevent.dto;

import java.util.List;

/** 등수(admin-web {@code RewardTier}) — 요청·응답 같은 모양. 이름 1~20자 · 인원(선착순은 인원과 같고, 일괄은 0) · 보상 줄 1~10 */
public record RewardTierDto(String id, String name, Integer quota, List<RewardLineDto> rewards) {
}
