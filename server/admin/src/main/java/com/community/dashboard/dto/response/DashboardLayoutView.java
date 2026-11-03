package com.community.dashboard.dto.response;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * {@code GET}·{@code PUT /api/admin/me/dashboard} 응답 — admin-web {@code DashboardLayoutBody}. 저장한 적 없으면 {@code gadgets} 가 null 이다(키는 나간다).
 */
public record DashboardLayoutView(int version, int columns, List<Gadget> gadgets) {

    /** @param config 저장한 설정(빈 칸은 없다 — {@code {}} 일 수 있다) */
    public record Gadget(String id, String type, int x, int y, int w, int h, JsonNode config) {
    }
}
