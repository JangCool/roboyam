package com.community.sanction.dto.response;

/** 준 사람 — 그 게시판의 매니저·스텝(회원), {@code role} 줄 때의 자리 MANAGER · STAFF. 탈퇴했으면 닉네임이 null 일 수 있다 */
public record BoardSanctionIssuerView(String id, String nickname, String role) {
}
