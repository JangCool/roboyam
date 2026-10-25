package com.community.nicknameimage.dto.response;

/** 신청한 회원 — 지금 닉네임·등급 */
public record NicknameImageMemberView(String id, String nickname, String grade, String gradeLabel) {
}
