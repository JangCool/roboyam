package com.community.rewardevent.dto.response;

import com.community.rewardevent.dto.RewardOperatorRef;
import java.time.Instant;

/** 기록 한 줄(admin-web {@code RewardEventAudit}) — 감사 기록을 이 이벤트로 거른 것. 자동(시스템)은 운영자 null, {@code detail} 은 서버가 요청 언어로 쓴 한 줄 */
public record RewardEventAuditView(String id, Instant at, RewardOperatorRef operator, String action, String detail) {
}
