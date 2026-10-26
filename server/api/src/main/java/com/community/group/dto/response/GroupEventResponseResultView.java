package com.community.group.dto.response;

/** 참석 응답 바꾸기·지우기의 결과 — 내 응답(지웠으면 null)과 바뀐 뒤 응답별 인원 */
public record GroupEventResponseResultView(String myResponse, GroupEventCountsView counts) {
}
