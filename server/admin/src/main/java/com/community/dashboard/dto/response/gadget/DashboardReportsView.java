package com.community.dashboard.dto.response.gadget;

import java.time.Instant;
import java.util.List;

/**
 * {@code REPORTS} — 신고 대기·검토 중 건수 + 오래된 대기(먼저 신고된 차례, 최대 limit).
 *
 * @param oldest {@code reason} = 그 사건에 가장 많이 들어온 신고 사유 코드, {@code autoHidden} = 자동 숨김 기록이 있는 사건
 */
public record DashboardReportsView(long pending, long reviewing, List<Oldest> oldest) implements DashboardGadgetData {

    public record Oldest(String id, String reason, String targetType, Instant reportedAt, boolean autoHidden) {
    }
}
