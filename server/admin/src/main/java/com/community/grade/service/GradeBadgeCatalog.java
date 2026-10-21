package com.community.grade.service;

import java.util.List;

/**
 * 등급 뱃지로 고를 수 있는 색과 아이콘 — 고정 목록이다(자유 입력 금지). admin-web 의 {@code util-grade.ts} {@code BADGE_COLORS}·{@code BADGE_ICONS} 와 같다
 * (서로 어긋나면 화면이 고르게 한 값을 서버가 거절한다). 색 값은 화면이 PrimeVue 색 토큰으로 푼다.
 */
public final class GradeBadgeCatalog {

    public static final List<String> COLORS = List.of("gray", "blue", "indigo", "violet", "pink", "red", "orange", "amber", "green", "teal", "cyan");

    /** PrimeIcons 클래스 — {@code pi pi-<이름>} */
    public static final List<String> ICONS = List.of("circle", "circle-fill", "star", "star-fill", "trophy", "crown", "heart", "heart-fill", "bolt", "flag", "bookmark",
            "shield", "verified", "sparkles", "megaphone", "thumbs-up", "check-circle", "gift", "key", "lightbulb", "face-smile", "user").stream()
            .map(name -> "pi pi-" + name).toList();

    private GradeBadgeCatalog() {
    }
}
