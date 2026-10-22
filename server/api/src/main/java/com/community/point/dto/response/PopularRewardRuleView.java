package com.community.point.dto.response;

/** 인기글 보상 규칙 — 어제 인기글 순위 상위 {@code topN} 위 글쓴이에게 글마다 {@code points}(0 이면 꺼짐). 인기글 기능이 아직 없어 지급은 없다 */
public record PopularRewardRuleView(int topN, int points) {
}
