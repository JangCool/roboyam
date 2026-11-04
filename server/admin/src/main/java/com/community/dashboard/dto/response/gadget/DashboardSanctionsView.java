package com.community.dashboard.dto.response.gadget;

import java.time.Instant;
import java.util.List;

/**
 * {@code SANCTIONS} — 진행 중인 정지·영구 정지 수 · 이의제기 대기 수 + 대기 오래된 차례 3건({@code kind} 는 요청 언어의 제재 이름 — 「경고 2회째」·「정지 7일」).
 * member-sanction-contract, admin-web {@code DashboardSanctions}.
 */
public record DashboardSanctionsView(long active, long appealsPending, List<Appeal> appeals) implements DashboardGadgetData {

    public record Appeal(String id, String nickname, String kind, Instant appealedAt) {
    }
}
