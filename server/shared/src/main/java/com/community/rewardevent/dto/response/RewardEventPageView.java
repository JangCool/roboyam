package com.community.rewardevent.dto.response;

import java.util.List;
import java.util.Map;

/** 목록 한 쪽(admin-web {@code RewardEventPage}) — 번호 내림차순, {@code counts} = 검색어·방식을 적용한 상태별 건수(읽을 때의 상태) */
public record RewardEventPageView(List<RewardEventRowView> items, long total, Map<String, Long> counts) {
}
