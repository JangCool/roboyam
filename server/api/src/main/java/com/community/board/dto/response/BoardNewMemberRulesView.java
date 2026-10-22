package com.community.board.dto.response;

/** 새 회원(첫 등급) 규칙(user-web {@code BoardNewMemberRules}) — {@code applies}: 지금 사용자가 첫 등급 회원이다(매니저·스텝 제외) */
public record BoardNewMemberRulesView(boolean applies, int linkLimit, int imageLimit) {
}
