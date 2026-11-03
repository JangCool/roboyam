package com.community.dashboard.dto.response.gadget;

import java.time.Instant;
import java.util.List;

/** {@code RECENT_SIGNUPS} — 최근 가입 회원(최근 차례, 탈퇴 제외, 최대 limit). {@code method} = 비밀번호가 있으면 EMAIL, 아니면 가장 먼저 연결한 소셜 */
public record DashboardRecentSignupsView(List<Item> items) implements DashboardGadgetData {

    public record Item(String id, String nickname, String method, Instant joinedAt) {
    }
}
