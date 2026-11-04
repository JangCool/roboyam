package com.community.sanction.dto.response;

import java.time.Instant;

/** 이의제기 목록 한 줄의 제재 요약 */
public record AppealSanctionBriefView(String id, long no, String kind, String status, Integer warningNo, Instant startsAt, Instant endsAt) {
}
