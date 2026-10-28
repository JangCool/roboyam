package com.community.home.dto;

import java.util.List;

/**
 * 홈 격자 배치 — 칸 수(12·24) + 가젯마다 자리. 공유 레이어 {@code @roboyam/home-layout} 의 {@code HomeLayout} 과 같은 모양.
 *
 * @param columns 격자 칸 수 12 · 24
 */
public record HomeGridLayout(int columns, List<HomeLayoutItem> items) {

    public HomeGridLayout {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
