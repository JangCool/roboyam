package com.community.auth.dto.request;

/**
 * {@code POST /api/auth/signup} — 이메일 가입. 어노테이션 검사 없이 받고 서비스가 위반을 칸별로 한꺼번에 모은다
 * ({@code email} → {@code password} → {@code nickname} → {@code agreeTerms} → {@code agreePrivacy} → {@code ageConfirmed}).
 * {@link #toString()} 은 이메일·비밀번호를 싣지 않는다.
 *
 * @param agreeTerms   이용약관 동의 — true 여야 한다
 * @param agreePrivacy 개인정보 처리방침 동의 — true 여야 한다
 * @param ageConfirmed 만 14세 이상 확인 — true 여야 한다
 */
public record SignupRequest(String email, String password, String nickname, Boolean agreeTerms, Boolean agreePrivacy, Boolean ageConfirmed) {

    @Override
    public String toString() {
        return "SignupRequest[email=(감춤), password=(감춤), nickname=" + nickname + ", agreeTerms=" + agreeTerms + ", agreePrivacy=" + agreePrivacy
                + ", ageConfirmed=" + ageConfirmed + "]";
    }
}
