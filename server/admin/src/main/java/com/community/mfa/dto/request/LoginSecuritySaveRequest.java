package com.community.mfa.dto.request;

/**
 * 로그인 보안 설정 저장(루트만). 범위 검사는 서비스가 칸 경로({@code enabled}·{@code graceDays}·{@code trustMonths}·{@code maxFailures})로 모은다.
 *
 * @param graceDays   1~90
 * @param trustMonths 1~12
 * @param maxFailures 3~10
 */
public record LoginSecuritySaveRequest(Boolean enabled, Integer graceDays, Integer trustMonths, Integer maxFailures) {
}
