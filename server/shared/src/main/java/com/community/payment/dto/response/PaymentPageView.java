package com.community.payment.dto.response;

import java.util.List;

/** 결제 화면 목록 한 쪽 — 거르기에 맞는 전체 수와 함께 */
public record PaymentPageView<T>(List<T> items, long total) {
}
