package com.community.menu.dto.response;

/**
 * 연결 유형 하나 — {@code GET /api/admin/menu/target-types}(admin-web {@code UtilMenu.TARGET_TYPES}·{@code SOON_TYPES} 의 서버 짝).
 *
 * @param label  요청 언어의 이름
 * @param picker 대상 고르기 모양 — 준비 중 유형은 null
 * @param ready  고를 수 있는가(준비 중 유형은 false)
 */
public record MenuTargetTypeView(String type, String label, String picker, boolean ready) {
}
