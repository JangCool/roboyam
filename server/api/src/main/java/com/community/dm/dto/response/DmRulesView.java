package com.community.dm.dto.response;

/** 쪽지 규칙(서버 상수) */
public record DmRulesView(int minAccountAgeDays, int bodyMax, int retentionDays) {
}
