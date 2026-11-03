package com.community.dashboard.dto.response.gadget;

/**
 * {@code SALES_SUMMARY} — 오늘 · 어제 · 이번 달(1일부터 오늘까지) · 지난달 같은 기간(지난달 1일부터 이번 달에 지난 만큼, 지난달 끝을 넘지 않음)의 현금 결제·환불(원). 날짜 경계는 한국 시간.
 * 결제 = 결제일({@code paid_at}) 기준 PG 가 알려 준 결제액, 환불 = 환불 완료일({@code completed_at}) 기준 끝난 PG 취소(commerce.md 16-2). 받아들인 결제만(이상 결제 · 결제 시험 제외).
 */
public record DashboardSalesSummaryView(Period today, Period yesterday, Period month, Period lastMonth) implements DashboardGadgetData {

    public record Period(long paid, long paidCount, long refunded, long refundedCount) {
    }
}
