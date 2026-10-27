package com.community.payment.dto.response;

import java.util.List;

/** 결제 이상 목록 한 쪽 — {@code openCount} 는 거르기와 무관한 처리 안 된 것 전체 수(제목 옆 숫자) */
public record PaymentAnomalyPageView(List<PaymentAnomalyRowView> items, long total, long openCount) {
}
