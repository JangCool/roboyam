package com.community.point.dto.response;

/**
 * 화면이 안내하는 지급 규칙(관리자 정책 — 저장하면 최대 15초 안에 바뀐다). community {@code rules} 모양.
 *
 * @param attendance          출석 포인트(출석 기능은 아직 없다)
 * @param post                글 포인트
 * @param comment             댓글 포인트
 * @param popularReward       인기글 보상 규칙(기능은 아직 없다)
 * @param nicknameTicketPrice 닉네임 변경권 가격(교환 기능은 아직 없다)
 * @param likeBonus           추천 받음 보너스 규칙 — {@code points}·{@code dailyCap} 이 0 이면 꺼짐
 */
public record PointRulesView(int attendance, int post, int comment, PopularRewardRuleView popularReward, int nicknameTicketPrice, LikeBonusRuleView likeBonus) {
}
