package com.community.dashboard.dto.response.gadget;

import java.time.Instant;
import java.util.List;

/**
 * {@code AD_SCHEDULE} — 앞으로 7일 안에 시작·종료하는 예약된 광고(시각 차례, 최대 8).
 *
 * @param items {@code placementName} = 건의 유형 코드(한도가 있는 상품 — {@code WIDE_BANNER} 등, 화면이 {@code bizApplication.adType.*} 로 옮긴다), {@code kind} START · END
 */
public record DashboardAdScheduleView(List<Item> items) implements DashboardGadgetData {

    public record Item(String id, String title, String placementName, String kind, Instant at) {
    }
}
