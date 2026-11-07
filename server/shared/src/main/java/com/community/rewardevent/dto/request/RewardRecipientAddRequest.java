package com.community.rewardevent.dto.request;

import java.util.List;

/** 당첨자 추가 — 등수 · 회원 번호들(500명까지) */
public record RewardRecipientAddRequest(String tierId, List<String> memberIds) {
}
