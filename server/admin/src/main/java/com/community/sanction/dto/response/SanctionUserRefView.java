package com.community.sanction.dto.response;

/** 제재 대상 회원 — {@code grade} 등급 키(모르면 null), {@code status} 회원 관리의 계정 상태와 같은 값 */
public record SanctionUserRefView(String id, String nickname, String grade, String status) {
}
