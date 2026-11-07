package com.community.rewardevent.dto.request;

/** 댓글 추첨 — 등수 · 뽑을 수(1 ~ 남은 자리) */
public record RewardDrawRequest(String tierId, Integer count) {
}
