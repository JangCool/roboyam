package com.community.rewardevent.dto.request;

import com.community.rewardevent.dto.RewardBulkConfig;
import com.community.rewardevent.dto.RewardFirstComeConfig;
import com.community.rewardevent.dto.RewardSelectionConfig;
import com.community.rewardevent.dto.RewardTierDto;
import java.time.Instant;
import java.util.List;

/**
 * 만들기·고치기 본문(admin-web {@code RewardEventSaveRequest}) — 검사 순서·칸 경로({@code tiers[0].rewards[1].points})가 계약이라 어노테이션 없이 받고 서비스가 모은다.
 * 고치기는 상태별 범위(준비 FULL · 진행 중 ACTIVE · 마감 CLOSED · 지급 끝·취소 MEMO) 밖의 칸이 바뀌면 {@code 409 REWARD_EVENT_LOCKED_FIELD}.
 */
public record RewardEventSaveRequest(String name, String memo, Instant startAt, Instant endAt, String noticeUrl, String mode, RewardFirstComeConfig firstCome,
                                     RewardSelectionConfig selection, RewardBulkConfig bulk, Boolean excludeOperators, List<RewardTierDto> tiers) {
}
