package com.community.emoticon.dto.request;

import java.util.List;

/**
 * 로보티콘 설정 저장 본문 — admin-web {@code EmoticonSettingsSaveRequest}.
 * 모든 칸을 저장한다(유료 판매 켜기 · 수수료율 · 정산일 · 최소 지급액 · 환불 기간 · 가격 단계 — S2). 빠진 칸은 {@code 400 fields.<칸>}.
 */
public record EmoticonSettingsSaveRequest(Integer rewardPerAcquire, Integer rewardDailyCap, Integer itemMin, Integer itemMax, Integer pendingLimit,
                                          Integer feeRatePercent, Integer settlementDay, Integer payoutMinimum, Integer refundDays, Boolean paidSalesEnabled,
                                          List<Integer> priceTiers) {
}
