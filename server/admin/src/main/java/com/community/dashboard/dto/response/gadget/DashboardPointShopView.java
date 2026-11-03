package com.community.dashboard.dto.response.gadget;

import java.util.List;

/** {@code POINTSHOP} — 오늘(한국 날짜) 구매 수 + 남은 수량 10개 이하 품목(적은 차례, 최대 5 — 판매 중·수량 제한 품목만). 이름은 {@code name}·{@code nameEn} */
public record DashboardPointShopView(long purchasesToday, List<LowStock> lowStock) implements DashboardGadgetData {

    public record LowStock(String id, String name, String nameEn, int stock) {
    }
}
