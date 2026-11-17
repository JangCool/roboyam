package com.community.title.entity;

import java.util.Optional;

/**
 * 단계 칭호가 세는 것({@code titles.tier_metric}) — 인기글 진입 일수 · 출석 일수 · 글 수(첫 {@code POSTS} 조건의 게시판) · 체험단 약속 지킴(체험단 기능 전에는 늘 0 — 리뷰어는 승인 때 단계 없음).
 */
public enum TitleMetric {
    POPULAR_DAYS,
    ATTENDANCE_DAYS,
    POSTS,
    TRIAL_KEPT;

    public static Optional<TitleMetric> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        for (TitleMetric m : values()) {
            if (m.name().equals(value.strip())) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }
}
