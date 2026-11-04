package com.community.dashboard.dto.response.gadget;

import java.util.List;

/**
 * {@code POPULAR_PAGES} — 오늘 많이 본 페이지 limit(5|10)개(조회 차례). 원본 기록을 켰을 때 1분마다 더한 페이지별 일별 집계({@code analytics_page_daily})를 읽는다.
 * {@code title} = 글이면 글 제목, 게시판이면 게시판 이름, 공구·소모임·포인트 상점 품목이면 그 이름 — 모르면 null(화면은 경로를 보인다). {@code titleEn} 은 영어 이름이 있는 것만.
 */
public record DashboardPopularPagesView(List<Item> items) implements DashboardGadgetData {

    public record Item(String path, String kind, String title, String titleEn, long views) {
    }
}
