package com.community.home.dto;

import java.util.List;

/**
 * 홈 격자 배치 — 칸 수(12·24) + 가젯마다 자리 + 모바일 순서. 공유 레이어 {@code @irobit/home-layout} 의 {@code HomeLayout} 과 같은 모양.
 *
 * @param columns     격자 칸 수 12 · 24
 * @param mobileOrder 모바일 한 열 순서 — 가젯 ID 목록(home-news-contract 「모바일 순서」). null(빈 목록도 null 로) = PC 배치에서 계산.
 *                    목록에 없는 가젯은 화면이 계산된 차례대로 그 뒤에 붙인다. 배치에 없는 ID·겹친 ID 를 버리는 것은 저장하는 쪽({@code GadgetLayoutWriter})이 한다
 */
public record HomeGridLayout(int columns, List<HomeLayoutItem> items, List<String> mobileOrder) {

    public HomeGridLayout {
        items = items == null ? List.of() : List.copyOf(items);
        mobileOrder = mobileOrder == null || mobileOrder.isEmpty() ? null : List.copyOf(mobileOrder);
    }

    /** 모바일 순서 없음(PC 배치에서 계산) */
    public HomeGridLayout(int columns, List<HomeLayoutItem> items) {
        this(columns, items, null);
    }
}
