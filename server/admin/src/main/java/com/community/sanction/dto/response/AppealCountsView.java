package com.community.sanction.dto.response;

/** 이의제기 상태별 전체 건수(거르기와 무관) */
public record AppealCountsView(long pending, long accepted, long rejected) {
}
