package com.community.rewardevent.dto.request;

import java.util.List;

/** 회수 — 받는 사람 번호들(500명까지) · 사유 5~200자(회원 알림에 보인다) */
public record RewardRevokeRequest(List<String> recipientIds, String reason) {
}
