package com.community.dashboard.dto.response.gadget;

import java.time.Instant;
import java.util.List;

/** {@code GROUP_REQUESTS} — 소모임 개설 신청 대기 건수 + 최근 신청(최근 차례, 최대 limit) */
public record DashboardGroupRequestsView(long pending, List<Recent> recent) implements DashboardGadgetData {

    public record Recent(String id, String name, String requesterNickname, Instant requestedAt) {
    }
}
