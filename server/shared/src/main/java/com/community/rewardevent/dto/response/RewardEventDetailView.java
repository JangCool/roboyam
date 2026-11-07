package com.community.rewardevent.dto.response;

import com.community.rewardevent.dto.RewardBulkConfig;
import com.community.rewardevent.dto.RewardFirstComeConfig;
import com.community.rewardevent.dto.RewardOperatorRef;
import com.community.rewardevent.dto.RewardSelectionConfig;
import com.community.rewardevent.dto.RewardTierDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 상세(admin-web {@code RewardEventDetail} — 저장 본문 + 상태·숫자·동작). {@code status} 는 읽을 때의 상태(끝 시각이 지난 진행 중은 마감 — 다음 쓰기 때 고정한다).
 * {@code recipientsByTier} = 등수별 지금 받는 사람 수, {@code runningRun} = 나눠 처리 중인 실행(없으면 null).
 */
public record RewardEventDetailView(String id, long no, String name, String memo, Instant startAt, Instant endAt, String noticeUrl, String mode,
                                    RewardFirstComeConfig firstCome, RewardSelectionConfig selection, RewardBulkConfig bulk, boolean excludeOperators,
                                    List<RewardTierDto> tiers, String status, RewardEventStatsView stats, Map<String, Long> recipientsByTier, RewardEventActionsView actions,
                                    RewardRunView runningRun, String cancelReason, Instant canceledAt, RewardOperatorRef createdBy, Instant createdAt, Instant updatedAt) {
}
