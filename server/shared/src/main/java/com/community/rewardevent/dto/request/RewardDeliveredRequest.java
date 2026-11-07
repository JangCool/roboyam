package com.community.rewardevent.dto.request;

/** 직접 전달 완료 — 보상 줄 · 메모 0~200자(개인정보 금지) */
public record RewardDeliveredRequest(String lineId, String memo) {
}
