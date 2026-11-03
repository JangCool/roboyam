package com.community.dashboard.dto.request;

import java.util.Map;

/**
 * {@code GET /api/admin/dashboard/gadgets?types=A,B&limit.A=5&days.B=30} 의 질의 — 컨트롤러가 질의 문자열에서 모은다. 검사는 서비스가 한다.
 *
 * @param types  쉼표로 이은 가젯 종류
 * @param limits 종류 → {@code limit.<종류>} 값(글자 그대로)
 * @param days   종류 → {@code days.<종류>} 값(글자 그대로)
 */
public record DashboardGadgetsRequest(String types, Map<String, String> limits, Map<String, String> days) {

    public DashboardGadgetsRequest {
        limits = limits == null ? Map.of() : Map.copyOf(limits);
        days = days == null ? Map.of() : Map.copyOf(days);
    }
}
