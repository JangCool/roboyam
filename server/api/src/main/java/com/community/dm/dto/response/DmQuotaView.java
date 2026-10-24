package com.community.dm.dto.response;

/** 오늘(한국 시간)의 한도 — user-web {@code DmQuota}. {@code resetsInSeconds} 는 다음 한국 시간 0시까지 */
public record DmQuotaView(int dailyLimit, int sentToday, int remainingToday, int newPartnerLimit, int newPartnersToday, int remainingNewPartners,
                          long resetsInSeconds) {
}
