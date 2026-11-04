package com.community.sanction.dto.response;

/** 반복 검토 전체 건수(거르기와 무관) */
public record BoardSanctionReviewCountsView(long open, long closed) {
}
