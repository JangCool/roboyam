package com.community.sanction.dto.response;

import java.time.Instant;

/** 내 제재에 붙은 이의제기 — 대기 중이면 {@code answer}·{@code answeredAt} 은 null(user-web {@code AppealView}) */
public record MyAppealView(String id, long no, String status, String reason, String answer, Instant createdAt, Instant answeredAt) {
}
