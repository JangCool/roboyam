package com.community.avatarupload.dto.response;

/** 신청한 회원 — 지금 닉네임·등급(등급 이름이 없으면 키) */
public record AvatarUploadMemberView(String id, String nickname, String grade, String gradeLabel) {
}
