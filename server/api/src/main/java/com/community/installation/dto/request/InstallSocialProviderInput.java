package com.community.installation.dto.request;

/**
 * 소셜 로그인 키 한 건 입력. {@link #toString()} 은 시크릿을 감춘다.
 *
 * @param provider {@code NAVER} · {@code KAKAO} · {@code GOOGLE}
 * @param enabled  생략하면 true
 */
public record InstallSocialProviderInput(String provider, String clientId, String clientSecret, Boolean enabled) {

    @Override
    public String toString() {
        return "InstallSocialProviderInput[provider=" + provider + ", clientId=" + clientId + ", clientSecret="
                + (clientSecret == null ? "null" : "(감춤)") + ", enabled=" + enabled + "]";
    }
}
