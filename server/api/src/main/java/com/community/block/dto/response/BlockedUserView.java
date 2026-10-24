package com.community.block.dto.response;

import java.time.Instant;

/** 내가 차단한 회원 한 줄 — user-web {@code BlockedUserView}. 닉네임·등급은 읽을 때의 현재 값, {@code role} 은 회원이면 늘 USER */
public record BlockedUserView(String id, String nickname, String grade, String gradeLabel, String role, Instant blockedAt) {
}
