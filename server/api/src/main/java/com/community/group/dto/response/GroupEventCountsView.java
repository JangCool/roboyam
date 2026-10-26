package com.community.group.dto.response;

/** 일정의 응답별 인원 — 지금 멤버의 응답만 센다 */
public record GroupEventCountsView(int going, int notGoing, int maybe) {
}
