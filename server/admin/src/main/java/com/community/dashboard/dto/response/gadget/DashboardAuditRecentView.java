package com.community.dashboard.dto.response.gadget;

import java.time.Instant;
import java.util.List;

/** {@code AUDIT_RECENT} — 최근 운영자 활동(성공한 운영자 작업, 최근 차례, 최대 limit). {@code summary} = 요청 언어의 작업 이름({@code audit.action.*}) */
public record DashboardAuditRecentView(List<Item> items) implements DashboardGadgetData {

    public record Item(String id, String actorNickname, String summary, Instant at) {
    }
}
