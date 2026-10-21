package com.community.adminauth.dto.response;

/**
 * 승인번호 요청의 응답 — <b>가입 여부와 무관하게 늘 같은 모양과 값</b>이다(계정 존재를 알리지 않는다). 화면이 [승인번호 다시 받기] 카운트다운에 쓴다.
 *
 * @param codeLength       승인번호 자릿수(숫자)
 * @param expiresInMinutes 승인번호가 유효한 분
 * @param cooldownSeconds  다시 받을 수 있을 때까지의 초
 */
public record PasswordResetCodeResponse(int codeLength, int expiresInMinutes, int cooldownSeconds) {
}
