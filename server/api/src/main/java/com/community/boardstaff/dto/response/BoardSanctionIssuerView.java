package com.community.boardstaff.dto.response;

/** 처리자 — 그 게시판 매니저가 보는 내역에만 온다(결정 7). {@code role} MANAGER · STAFF · OPERATOR(줄 때의 자리 — OPERATOR 는 운영자 회원 계정, 닉네임 = 그 회원 닉네임) */
public record BoardSanctionIssuerView(String nickname, String role) {
}
