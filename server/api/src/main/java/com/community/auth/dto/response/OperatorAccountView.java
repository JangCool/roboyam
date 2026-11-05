package com.community.auth.dto.response;

/**
 * 로그인 정보의 {@code operatorAccount}(user-web {@code OperatorAccountLink}) — 이 회원 계정이 관리자 앱 운영자에게 이어져 있고 힘이 살아 있을 때만
 * (docs/features/operator-member-account-contract.md 정책 6). 계정 화면 읽기 전용 카드 · 탈퇴 화면 막힘 안내가 쓴다.
 *
 * @param operatorName 이어진 운영자의 표시 이름
 */
public record OperatorAccountView(String operatorName) {
}
