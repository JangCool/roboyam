package com.community.hotdeal.dto.response;

/** 저장은 됐지만 알릴 것(user-web {@code DealWarning}) — 예 {@code DEAL_LINK_BLOCKED}(고치기 때 지금 규칙으로 다시 판정했더니 차단 도메인) */
public record DealWarningView(String code, String message) {
}