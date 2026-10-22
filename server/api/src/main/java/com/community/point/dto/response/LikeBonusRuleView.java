package com.community.point.dto.response;

/** 추천 받음 보너스 — 인정된 추천 {@code threshold} 개에 {@code points}, 하루 {@code dailyCap} 번(글·댓글 합산). points·dailyCap 이 0 이면 꺼짐 */
public record LikeBonusRuleView(int threshold, int points, int dailyCap) {
}
