package com.community.sanction.dto.response;

import java.time.Instant;

/** 이의제기 목록 한 줄(admin-web {@code AppealItem}) */
public record AppealItemView(String id, long no, String status, SanctionUserRefView user, AppealSanctionBriefView sanction, Instant createdAt, Instant answeredAt) {
}
