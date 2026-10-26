package com.community.point.dto.response;

/**
 * 오늘(한국 시간) 활동 적립 합과 하루 총 한도(커머스 1-3) — 출석·연속 보너스·글·댓글·추천 보너스로 지급한 양의 합(미회수 상계 전, 지운 글로 받은 것도 센다).
 * 인기글 보상·운영 조정·충전 보너스·가입 포인트는 넣지 않는다.
 *
 * @param amount 오늘 지급한 활동 적립 합(P)
 * @param cap    정책 {@code dailyTotalCap}(0 = 총 한도 없음)
 */
public record PointTotalView(long amount, int cap) {
}
