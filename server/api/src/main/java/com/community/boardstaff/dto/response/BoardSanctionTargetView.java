package com.community.boardstaff.dto.response;

/** 제재 대상 회원 — 닉네임은 읽을 때의 값(user-web {@code BoardSanctionTarget}) */
public record BoardSanctionTargetView(String userId, String nickname) {
}
