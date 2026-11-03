package com.community.dashboard.dto.response.gadget;

import java.util.List;

/** {@code HOTDEAL_TOP} — 지금 인기 핫딜 TOP 5(최근 7일 등록 · 진행 중 · 공개 글, 추천 많은 차례). {@code price} = 딜 가격(원) */
public record DashboardHotdealTopView(List<Item> items) implements DashboardGadgetData {

    public record Item(String id, String shopName, String title, int likes, Long price) {
    }
}
