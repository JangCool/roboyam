package com.community.sanction.dto.response;

/** 부여 대화창의 회원 찾기 한 줄 — {@code warningCount} 유효한 경고 수(0~2) */
public record SanctionTargetView(String id, String nickname, String grade, String status, int warningCount) {
}
