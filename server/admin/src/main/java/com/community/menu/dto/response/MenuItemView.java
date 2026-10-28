package com.community.menu.dto.response;

import tools.jackson.databind.JsonNode;

/**
 * 항목 — admin-web {@code MenuItemDto}. 모르는 유형은 {@code targetType}·{@code targetId}·{@code targetKey}·{@code settings} 를 저장된 그대로 돌려준다.
 *
 * @param divider     상단 가로 메뉴 첫 줄 구분선 {@code NONE} · {@code BEFORE} · {@code AFTER}
 * @param description 한 줄 설명(메뉴 설명 줄, 80자까지) — 없으면 null
 * @param resolved 서버가 본 상태({@code OK}·{@code BOARD_INACTIVE}·{@code SALES_OFF}·{@code TARGET_MISSING}·{@code UNKNOWN_TYPE}) — 화면 타입에는 없는 덧붙임 칸이다(화면은 스스로 계산한다)
 */
public record MenuItemView(String id, String kind, String label, String nameEn, boolean visible, String audience, String minGrade, String targetType,
                           String targetId, String targetKey, JsonNode settings, boolean mobileTab, String divider, String description, Resolved resolved)
        implements MenuTreeNodeView {

    /** 서버가 본 항목 상태 */
    public record Resolved(String status) {
    }
}
