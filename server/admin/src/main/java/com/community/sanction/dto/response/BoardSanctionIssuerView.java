package com.community.sanction.dto.response;

/**
 * 준 사람 — 그 게시판의 매니저·스텝(회원), {@code role} 줄 때의 자리 MANAGER · STAFF. 탈퇴했으면 닉네임이 null 일 수 있다.
 * 운영자 회원 계정이 줬으면 {@code role} OPERATOR(닉네임 = 그 회원 닉네임) + {@code operatorName}(운영자의 지금 이름 — operator-member-account-contract), 그 밖 null.
 */
public record BoardSanctionIssuerView(String id, String nickname, String role, String operatorName) {
}
