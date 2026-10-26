package com.community.pointshop.entity;

import java.util.Arrays;

/**
 * 프로필 테두리 팔레트 키 — V86 {@code ck_point_shop_items_border}·두 앱 {@code UtilPointShop.BORDERS} 와 같은 목록(코드 고정, 화면 확정 「프로필 테두리 팔레트」).
 * 그리는 법(그라데이션 고리)은 화면 몫이다.
 */
public enum ProfileBorderStyle {
    GOLD,
    SILVER,
    ROSE,
    OCEAN,
    FOREST,
    NIGHT,
    RAINBOW;

    public static boolean known(String key) {
        return key != null && Arrays.stream(values()).anyMatch(style -> style.name().equals(key));
    }
}
