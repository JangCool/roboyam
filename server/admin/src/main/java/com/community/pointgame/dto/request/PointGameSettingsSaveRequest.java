package com.community.pointgame.dto.request;

/**
 * {@code PUT /api/admin/point-games/settings} — 두 칸을 한 번에(서비스가 검사 — {@code fields.lotteryProviderName}·{@code fields.lotteryCheckUrl}).
 * 복권 사업자 이름 1~30자 · 당첨 확인 링크 {@code https://} 절대 주소 300자 이하.
 */
public record PointGameSettingsSaveRequest(String lotteryProviderName, String lotteryCheckUrl) {
}
