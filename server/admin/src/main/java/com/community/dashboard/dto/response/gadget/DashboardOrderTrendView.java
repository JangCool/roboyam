package com.community.dashboard.dto.response.gadget;

import java.time.LocalDate;
import java.util.List;

/** {@code ORDER_TREND} — 일별(한국 날짜) 현금 결제 금액·건수, 오래된 차례 · 오늘 포함 7|30일(결제가 없는 날은 0). 결제 기준은 {@link DashboardSalesSummaryView} 와 같다 */
public record DashboardOrderTrendView(List<Day> days) implements DashboardGadgetData {

    public record Day(LocalDate date, long amount, long count) {
    }
}
