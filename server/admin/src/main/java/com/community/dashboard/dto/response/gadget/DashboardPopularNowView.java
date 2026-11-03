package com.community.dashboard.dto.response.gadget;

import java.util.List;

/** {@code POPULAR_NOW} — 지금 인기글(실시간 순위 스냅샷, 공개 인기글과 같은 거름 · 운영자가 뺀 글 제외, 최대 limit) */
public record DashboardPopularNowView(List<Item> items) implements DashboardGadgetData {

    public record Item(String postId, String boardName, String boardNameEn, String title, int likes) {
    }
}
