package com.community.rewardevent.dto.request;

import com.community.rewardevent.dto.RewardBulkConfig;

/** 일괄 대상 미리 보기(저장 전에도 쓴다) */
public record RewardTargetPreviewRequest(RewardBulkConfig bulk, Boolean excludeOperators) {
}
