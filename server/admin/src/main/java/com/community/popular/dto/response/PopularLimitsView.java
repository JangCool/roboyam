package com.community.popular.dto.response;

/** 인기글 설정의 숫자 칸 범위(서버 {@code PopularSettingsService} 와 V74 CHECK 가 같다). {@code weekStart}·{@code excludeStaffBoards} 는 범위가 없다 */
public record PopularLimitsView(PopularLimitView likeWeight, PopularLimitView commentWeight, PopularLimitView viewWeight, PopularLimitView gravity,
                                PopularLimitView ageOffsetHours, PopularLimitView minLikes, PopularLimitView minComments, PopularLimitView minViews,
                                PopularLimitView maxPerAuthor) {
}
