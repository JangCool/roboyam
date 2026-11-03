package com.community.dashboard.dto.request;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * {@code PUT /api/admin/me/dashboard} — admin-web {@code DashboardLayoutBody}. 검사는 서비스가 칸 경로({@code gadgets[i].x})로 모은다.
 *
 * @param version 저장 모양 판(지금 2)
 * @param columns 격자 칸 수(지금 48)
 * @param gadgets 배치된 가젯(최대 40)
 */
public record DashboardLayoutRequest(Integer version, Integer columns, List<Gadget> gadgets) {

    /**
     * @param config 설정 — 종류에 해당하는 칸만 검사·저장하고 나머지 칸은 버린다(화면 {@code readConfig} 와 같다)
     */
    public record Gadget(String id, String type, Integer x, Integer y, Integer w, Integer h, JsonNode config) {
    }
}
