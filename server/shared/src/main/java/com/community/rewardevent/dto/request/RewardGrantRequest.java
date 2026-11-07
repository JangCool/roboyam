package com.community.rewardevent.dto.request;

/** 지급 실행 — 멱등 키(이벤트마다 유일, 1~100자 — 같은 키는 처음 실행을 돌려준다) */
public record RewardGrantRequest(String idempotencyKey) {
}
