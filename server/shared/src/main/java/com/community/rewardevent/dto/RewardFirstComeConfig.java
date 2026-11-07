package com.community.rewardevent.dto;

/** 선착순 설정(admin-web {@code RewardEventFirstComeConfig}) — 기준 사건 {@code IDENTITY_VERIFIED}·{@code EMAIL_VERIFIED} · 인원 1~100,000 */
public record RewardFirstComeConfig(String trigger, Integer limit) {
}
