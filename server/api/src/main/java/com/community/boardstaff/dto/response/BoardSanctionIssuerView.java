package com.community.boardstaff.dto.response;

/** 처리자 — 그 게시판 매니저가 보는 내역에만 온다(결정 7). {@code role} MANAGER · STAFF(줄 때의 자리) */
public record BoardSanctionIssuerView(String nickname, String role) {
}
