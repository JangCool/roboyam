package com.community.dashboard.dto.response.gadget;

import java.time.Instant;
import java.util.List;

/**
 * {@code TODAY_SCHEDULE} — 지금부터 내일(한국 날짜) 끝까지의 일정(시각 차례, 최대 10). 항목마다 그 종류 조회 권한이 있을 때만 든다.
 *
 * @param items {@code kind} AD_START · AD_END · GROUPBUY_CLOSE · POINTSHOP_START · POINTSHOP_END · BATCH. {@code titleEn} = 영어 이름이 있는 항목(포인트 상점 품목)만
 */
public record DashboardTodayScheduleView(List<Item> items) implements DashboardGadgetData {

    public record Item(String id, String kind, String title, String titleEn, Instant at) {
    }
}
