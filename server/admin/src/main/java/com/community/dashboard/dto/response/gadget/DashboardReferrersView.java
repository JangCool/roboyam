package com.community.dashboard.dto.response.gadget;

import java.util.List;

/**
 * {@code REFERRERS} — 오늘 방문(세션 첫 페이지 기준)을 유입처 분류별로(SEARCH · SOCIAL · DIRECT · OTHER_SITE · CAMPAIGN 다섯 모두, 많은 차례 — 0 포함) + 상위 유입 도메인 5
 * (직접·캠페인은 도메인이 없어 빠진다).
 */
public record DashboardReferrersView(long total, List<Group> groups, List<Domain> domains) implements DashboardGadgetData {

    public record Group(String group, long visits) {
    }

    public record Domain(String domain, long visits) {
    }
}
