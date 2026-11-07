package com.community.rewardevent.dto.request;

/** 취소 — 사유 5~200자(서비스가 본다) */
public record RewardCancelRequest(String reason) {
}
