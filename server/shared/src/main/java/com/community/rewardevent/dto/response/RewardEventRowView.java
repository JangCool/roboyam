package com.community.rewardevent.dto.response;

import com.community.rewardevent.dto.RewardOperatorRef;
import com.community.rewardevent.dto.RewardTierDto;
import java.time.Instant;
import java.util.List;

/** 목록 한 줄(admin-web {@code RewardEventRow}) — 상태는 읽을 때의 상태(끝 시각이 지난 진행 중은 마감) */
public record RewardEventRowView(String id, long no, String name, String mode, String status, Instant startAt, Instant endAt, List<RewardTierDto> tiers,
                                 Integer firstComeLimit, RewardEventStatsView stats, RewardOperatorRef createdBy, Instant createdAt) {
}
